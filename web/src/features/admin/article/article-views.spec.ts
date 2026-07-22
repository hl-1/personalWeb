import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import type { Plugin } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import type { AdminClient } from '../admin-client'
import { AdminApiError } from '../admin-client'
import MarkdownEditor from '../components/MarkdownEditor.vue'
import AdminArticleListView from '../../../views/admin/AdminArticleListView.vue'
import AdminArticleEditView from '../../../views/admin/AdminArticleEditView.vue'
import { useOperationFeedbackStore } from '../../../shared/feedback/operation-feedback'

const confirmAdminAction = vi.hoisted(() => vi.fn())
vi.mock('../admin-confirmation', () => ({ confirmAdminAction }))

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

async function plugins(path = '/admin/articles', pinia = createPinia()): Promise<
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
  return [router, queryPlugin, pinia]
}

describe('article administration views', () => {
  beforeEach(() => confirmAdminAction.mockReset())

  it('renders a compact list with draft actions', async () => {
    const pinia = createPinia()
    const adminClient = client()
    const wrapper = mount(AdminArticleListView, {
      props: { adminClient }, global: { plugins: await plugins('/admin/articles', pinia) },
    })
    await vi.waitFor(() => expect(wrapper.find('[data-testid="article-list"]').exists()).toBe(true))
    expect(wrapper.find('.el-table').exists()).toBe(true)
    expect(wrapper.find('.el-select').exists()).toBe(true)
    expect(wrapper.find('.el-input').exists()).toBe(true)
    expect(wrapper.find('.el-button').exists()).toBe(true)
    expect(wrapper.text()).toContain('Title')
    expect(wrapper.find('[data-testid="publish-article"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="archive-article"]').exists()).toBe(false)
    const updatedAt = wrapper.get('[data-testid="article-updated-at"]')
    expect(updatedAt.attributes('datetime')).toBe(instant)
    expect(updatedAt.attributes('title')).toBe(instant)
    expect(updatedAt.text()).not.toContain('T')
    expect(updatedAt.text()).not.toContain('Z')
    await wrapper.get('[data-testid="publish-article"]').trigger('click')
    await vi.waitFor(() => expect(adminClient.publishArticle).toHaveBeenCalled())
    expect(useOperationFeedbackStore(pinia).messages.at(-1)?.message).toBe('文章发布成功')
  })

  it('previews through the server and rejects unsafe HTML', async () => {
    const preview = vi.fn().mockResolvedValue({ html: '<p>Safe</p>' })
    const wrapper = mount(MarkdownEditor, { props: { modelValue: '# Text', preview } })
    expect(wrapper.find('.el-tabs').exists()).toBe(true)
    expect(wrapper.find('.el-textarea__inner').exists()).toBe(true)
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
    expect(wrapper.find('.el-form').exists()).toBe(true)
    expect(wrapper.find('.el-select').exists()).toBe(true)
    await wrapper.get('input[name="title"]').setValue('Unsaved title')
    await wrapper.get('[data-testid="save-article"]').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('Reload server version'))
    expect(wrapper.get<HTMLInputElement>('input[name="title"]').element.value).toBe('Unsaved title')
  })

  it('shows field guidance and blocks an invalid article before the request', async () => {
    const createArticle = vi.fn()
    const wrapper = mount(AdminArticleEditView, {
      props: { adminClient: client({ createArticle }) },
      global: { plugins: await plugins('/admin/articles/new') },
    })

    expect(wrapper.find('.el-form').exists()).toBe(true)

    expect(wrapper.get('[data-field="slug"] [data-testid="field-hint"]').text())
      .toContain('3-120')
    await wrapper.get('input[name="slug"]').setValue('a')
    await wrapper.get('input[name="slug"]').trigger('blur')
    expect(wrapper.get('[data-field="slug"] [data-testid="field-error"]').text())
      .toContain('at least 3')

    await wrapper.get('input[name="slug"]').setValue('article-one')
    expect(wrapper.find('[data-field="slug"] [data-testid="field-error"]').exists()).toBe(false)

    await wrapper.get('[data-testid="save-article"]').trigger('submit')
    expect(createArticle).not.toHaveBeenCalled()
    expect(wrapper.get('[data-field="title"] [data-testid="field-error"]').text()).toContain('required')
  })
  it.each([
    ['confirmed', '/admin/articles/new', ''],
    ['cancelled', '/admin/articles', 'New article'],
    ['closed', '/admin/articles/new', ''],
  ] as const)('handles the %s continue-adding result', async (result, expectedPath, expectedTitle) => {
    confirmAdminAction.mockResolvedValue(result)
    const pinia = createPinia()
    const wrapper = mount(AdminArticleEditView, {
      props: { adminClient: client() },
      global: { plugins: await plugins('/admin/articles/new', pinia) },
    })

    await wrapper.get('input[name="title"]').setValue('New article')
    await wrapper.get('input[name="slug"]').setValue('new-article')
    await wrapper.get('textarea[name="summary"]').setValue('Article summary')
    await wrapper.get('[data-testid="save-article"]').trigger('submit')

    await vi.waitFor(() => expect(confirmAdminAction).toHaveBeenCalledWith(expect.objectContaining({
      confirmButtonText: '继续添加',
      cancelButtonText: '返回列表',
    })))
    await vi.waitFor(() => expect(wrapper.vm.$route.path).toBe(expectedPath))
    expect(wrapper.get<HTMLInputElement>('input[name="title"]').element.value).toBe(expectedTitle)
    expect(useOperationFeedbackStore(pinia).messages.at(-1)?.message).toBe('文章添加成功')
  })
})
