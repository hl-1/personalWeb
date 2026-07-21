import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import type { Plugin } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { AdminClient } from '../admin-client'
import { AdminApiError } from '../admin-client'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import AdminArticleListView from '../../../views/admin/AdminArticleListView.vue'
import AdminArticleEditView from '../../../views/admin/AdminArticleEditView.vue'

const id = '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'
const instant = '2026-07-20T10:00:00Z'
const article = { id, slug: 'article', title: 'Title', summary: 'Summary', bodyMarkdown: '# Body',
  status: 'DRAFT' as const, categoryId: null, tagIds: [], seoTitle: null, seoDescription: null,
  publishedAt: null, createdAt: instant, updatedAt: instant, version: 0 }

function client(overrides: Partial<AdminClient> = {}): AdminClient {
  return {
    listArticles: vi.fn().mockResolvedValue({ items: [{ id, slug: 'article', title: 'Title',
      summary: 'Summary', status: 'DRAFT', publishedAt: null, updatedAt: instant, version: 0 }],
    page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    getArticle: vi.fn().mockResolvedValue(article),
    createArticle: vi.fn().mockResolvedValue({ data: article, location: `/api/v1/admin/articles/${id}` }),
    updateArticle: vi.fn().mockResolvedValue(article), deleteArticle: vi.fn(),
    publishArticle: vi.fn().mockResolvedValue({ ...article, status: 'PUBLISHED' }),
    archiveArticle: vi.fn().mockResolvedValue({ ...article, status: 'ARCHIVED' }),
    previewArticle: vi.fn().mockResolvedValue({ html: '<p>Preview</p>' }),
    listCategories: vi.fn().mockResolvedValue([]), listTags: vi.fn().mockResolvedValue([]),
    ...overrides,
  } as AdminClient
}

async function plugins(path = '/admin/articles'): Promise<
  Array<Plugin | [Plugin, { queryClient: QueryClient }]>
> {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/admin/articles', component: AdminArticleListView },
    { path: '/admin/articles/new', component: AdminArticleEditView },
    { path: '/admin/articles/:id', component: AdminArticleEditView },
  ] })
  await router.push(path)
  await router.isReady()
  const queryPlugin: [Plugin, { queryClient: QueryClient }] = [
    VueQueryPlugin,
    { queryClient: new QueryClient({ defaultOptions: { queries: { retry: false } } }) },
  ]
  return [router, queryPlugin, createPinia()]
}

describe('article administration views', () => {
  it('renders a compact list with draft actions', async () => {
    const wrapper = mount(AdminArticleListView, {
      props: { adminClient: client() }, global: { plugins: await plugins() },
    })
    await vi.waitFor(() => expect(wrapper.find('[data-testid="article-list"]').exists()).toBe(true))
    expect(wrapper.text()).toContain('Title')
    expect(wrapper.find('[data-testid="publish-article"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="archive-article"]').exists()).toBe(false)
  })

  it('previews through the server and rejects unsafe HTML', async () => {
    const preview = vi.fn().mockResolvedValue({ html: '<p>Safe</p>' })
    const wrapper = mount(MarkdownEditor, { props: { modelValue: '# Text', preview } })
    await wrapper.get('[data-testid="preview-tab"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('Safe'))
    expect(preview).toHaveBeenCalledWith('# Text')

    await wrapper.setProps({ preview: vi.fn().mockResolvedValue({ html: '<script>bad</script>' }) })
    await wrapper.get('[data-testid="edit-tab"]').trigger('click')
    await wrapper.get('[data-testid="preview-tab"]').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('Preview unavailable'))
    expect(wrapper.html()).not.toContain('<script>')
  })

  it('preserves form input and offers reload after stale_version', async () => {
    const updateArticle = vi.fn().mockRejectedValue(
      new AdminApiError('conflict', 409, 'stale_version'),
    )
    const adminClient = client({ updateArticle })
    const wrapper = mount(AdminArticleEditView, {
      props: { adminClient, id }, global: { plugins: await plugins(`/admin/articles/${id}`) },
    })
    await vi.waitFor(() => expect(wrapper.get('input[name="title"]').element).toHaveProperty('value', 'Title'))
    await wrapper.get('input[name="title"]').setValue('Unsaved title')
    await wrapper.get('[data-testid="save-article"]').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('Reload server version'))
    expect(wrapper.get<HTMLInputElement>('input[name="title"]').element.value).toBe('Unsaved title')
  })
})
