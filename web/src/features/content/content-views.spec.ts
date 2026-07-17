import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import ArticleDetailView from '../../views/ArticleDetailView.vue'
import BlogListView from '../../views/BlogListView.vue'
import SafeMarkdownView from '../../shared/content/SafeMarkdownView.vue'
import { parseSafeHtml } from '../../shared/content/safe-html'
import { ContentApiError, type ContentClient } from './content-client'

const article = {
  id: '00000000-0000-4000-8000-000000000001',
  slug: 'public-article',
  title: 'Public article with a long but readable title',
  summary: 'Article summary',
  category: 'java',
  tags: ['spring'],
  publishedAt: '2026-07-17T06:00:00Z',
  updatedAt: '2026-07-17T07:00:00Z',
}

function contentClient(overrides: Partial<ContentClient> = {}): ContentClient {
  return {
    getArticles: vi.fn().mockResolvedValue({
      items: [article], page: 0, size: 10, totalElements: 1, totalPages: 1,
    }),
    getArticle: vi.fn().mockImplementation(async (slug: string) => ({
      ...article,
      slug,
      title: `Article ${slug}`,
      contentHtml: parseSafeHtml('<h2>Safe content</h2><pre><code>long-code-line</code></pre>'),
      seoTitle: null,
      seoDescription: 'Article description',
      canonicalPath: `/blog/${slug}`,
    })),
    getCategories: vi.fn().mockResolvedValue([
      { name: 'Java', slug: 'java', publishedArticleCount: 1 },
    ]),
    getTags: vi.fn().mockResolvedValue([
      { name: 'Spring', slug: 'spring', publishedArticleCount: 1 },
    ]),
    ...overrides,
  }
}

describe('SafeMarkdownView', () => {
  it('renders approved HTML in the single markdown boundary', () => {
    const wrapper = mount(SafeMarkdownView, {
      props: { html: parseSafeHtml('<p><strong>Safe</strong></p>') },
    })

    expect(wrapper.html()).toContain('<strong>Safe</strong>')
  })

  it('rejects a dangerous runtime prop before it can render', () => {
    expect(() => Reflect.apply(mount, undefined, [SafeMarkdownView, {
      props: { html: '<script>unsafe()</script>' },
    }])).toThrowError(/safe HTML/i)
  })
})

describe('BlogListView', () => {
  let wrapper: VueWrapper | undefined
  afterEach(() => wrapper?.unmount())

  it('shows loading, empty, and request error states', async () => {
    let resolveArticles: ((value: unknown) => void) | undefined
    const pending = new Promise((resolve) => { resolveArticles = resolve })
    wrapper = await mountView(BlogListView, {
      contentClient: contentClient({ getArticles: vi.fn().mockReturnValue(pending) }),
    }, '/blog')
    expect(wrapper.get('[data-testid="loading-state"]').text()).toContain('Loading')
    resolveArticles?.({ items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })
    await flushPromises()
    expect(wrapper.get('[data-testid="empty-state"]').text()).toContain('No published articles')
    wrapper.unmount()

    wrapper = await mountView(BlogListView, {
      contentClient: contentClient({ getArticles: vi.fn().mockRejectedValue(new Error('failed')) }),
    }, '/blog')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('could not be loaded')
  })

  it('uses route filters and enforces pagination boundaries', async () => {
    const client = contentClient({
      getArticles: vi.fn().mockResolvedValue({
        items: [article], page: 1, size: 10, totalElements: 11, totalPages: 2,
      }),
    })
    wrapper = await mountView(
      BlogListView,
      { contentClient: client },
      '/blog?page=1&category=java&tag=spring',
    )
    await flushPromises()

    expect(client.getArticles).toHaveBeenCalledWith({
      page: 1, size: 10, category: 'java', tag: 'spring',
    })
    expect(wrapper.get('[data-testid="previous-page"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-testid="next-page"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="category-filter-java"]').attributes('href'))
      .toContain('category=java')
    expect(wrapper.get('[data-testid="tag-filter-spring"]').attributes('href'))
      .toContain('tag=spring')
  })
})

describe('ArticleDetailView', () => {
  it('renders invalid route slugs as not found without requesting the API', async () => {
    const client = contentClient()
    const wrapper = await mountView(
      ArticleDetailView,
      { contentClient: client, slug: 'ab' },
      '/blog/ab',
    )
    await flushPromises()

    expect(wrapper.get('[data-testid="not-found-state"]').text()).toContain('Article not found')
    expect(client.getArticle).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('switches directly between slugs without reusing stale detail', async () => {
    const client = contentClient()
    const wrapper = await mountView(
      ArticleDetailView,
      { contentClient: client, slug: 'first-article' },
      '/blog/first-article',
    )
    await flushPromises()
    expect(wrapper.text()).toContain('Article first-article')

    await wrapper.setProps({ slug: 'second-article' })
    await flushPromises()

    expect(wrapper.text()).toContain('Article second-article')
    expect(client.getArticle).toHaveBeenCalledWith('second-article')
    wrapper.unmount()
  })

  it('renders a stable 404 state without unpublished details', async () => {
    const client = contentClient({
      getArticle: vi.fn().mockRejectedValue(new ContentApiError({
        kind: 'not_found', status: 404, code: 'article_not_found',
      })),
    })
    const wrapper = await mountView(
      ArticleDetailView,
      { contentClient: client, slug: 'missing-article' },
      '/blog/missing-article',
    )
    await flushPromises()

    expect(wrapper.get('[data-testid="not-found-state"]').text()).toContain('Article not found')
    expect(wrapper.get('[data-testid="not-found-state"] h1').text()).toBe('Article not found')
    expect(wrapper.text()).not.toContain('article_not_found')
    wrapper.unmount()
  })
})

async function mountView(
  component: Parameters<typeof mount>[0],
  props: Record<string, unknown>,
  path: string,
): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:pathMatch(.*)*', component: { template: '<div />' } }],
  })
  await router.push(path)
  await router.isReady()
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  })
  return mount(component, {
    props,
    global: { plugins: [router, [VueQueryPlugin, { queryClient }]] },
  })
}
