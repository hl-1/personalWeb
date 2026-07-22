import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, type Pinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { AdminApiError, type AdminClient } from '../admin-client'
import AdminTaxonomyView from '../../../views/admin/AdminTaxonomyView.vue'
import { useOperationFeedbackStore } from '../../../shared/feedback/operation-feedback'

const confirmAdminAction = vi.hoisted(() => vi.fn())
vi.mock('../admin-confirmation', () => ({ confirmAdminAction }))

const id = '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'
const instant = '2026-07-20T10:00:00Z'
const row = { id, name: 'Java', slug: 'java', articleCount: 2,
  createdAt: instant, updatedAt: instant, version: 0 }

function client(overrides: Partial<AdminClient> = {}): AdminClient {
  return {
    listCategories: vi.fn().mockResolvedValue([row]), listTags: vi.fn().mockResolvedValue([]),
    createCategory: vi.fn().mockResolvedValue({ data: row, location: `/api/v1/admin/categories/${id}` }),
    createTag: vi.fn(), updateCategory: vi.fn().mockResolvedValue(row), updateTag: vi.fn(),
    deleteCategory: vi.fn(), deleteTag: vi.fn(),
    ...overrides,
  } as AdminClient
}

function mountView(
  adminClient: AdminClient,
  kind: 'category' | 'tag' = 'category',
  pinia: Pinia = createPinia(),
) {
  return mount(AdminTaxonomyView, {
    props: { adminClient, kind },
    global: {
      plugins: [[VueQueryPlugin, { queryClient: new QueryClient() }], pinia],
      stubs: { RouterLink: { template: '<a><slot /></a>' } },
    },
  })
}

describe('AdminTaxonomyView', () => {
  it('loads, creates, edits and confirms deletion for categories', async () => {
    const adminClient = client()
    const pinia = createPinia()
    confirmAdminAction.mockResolvedValue('confirmed')
    const wrapper = mountView(adminClient, 'category', pinia)
    await vi.waitFor(() => expect(wrapper.text()).toContain('Java'))
    expect(wrapper.find('.el-form').exists()).toBe(true)
    expect(wrapper.find('.el-table').exists()).toBe(true)
    expect(wrapper.find('.el-input').exists()).toBe(true)
    expect(wrapper.find('.el-button').exists()).toBe(true)

    await wrapper.get('[data-testid="edit-taxonomy"]').trigger('click')
    await wrapper.get('input[name="editName"]').setValue('JVM')
    await wrapper.get('[data-testid="save-taxonomy"]').trigger('submit')
    await vi.waitFor(() => expect(adminClient.updateCategory).toHaveBeenCalled())
    await vi.waitFor(() => expect(wrapper.find('[data-testid="save-taxonomy"]').exists()).toBe(false))
    expect(useOperationFeedbackStore(pinia).messages.at(-1)?.message).toBe('分类保存成功')
    await vi.waitFor(() => expect(wrapper.get('[data-testid="delete-taxonomy"]').attributes('disabled'))
      .toBeUndefined())

    await wrapper.get('[data-testid="delete-taxonomy"]').trigger('click')
    expect(confirmAdminAction).toHaveBeenCalledWith(expect.objectContaining({
      message: expect.stringContaining('2'),
    }))
    expect(adminClient.deleteCategory).toHaveBeenCalledWith(id, 0)
    await vi.waitFor(() => expect(useOperationFeedbackStore(pinia).messages.at(-1)?.message)
      .toBe('分类删除成功'))
  })

  it.each([
    ['duplicate_slug', 'Slug is already in use'],
    ['stale_version', 'Reload this row'],
    ['taxonomy_in_use', 'Remove article references first'],
  ])('preserves row input for %s', async (code, message) => {
    const updateCategory = vi.fn().mockRejectedValue(new AdminApiError('conflict', 409, code))
    const wrapper = mountView(client({ updateCategory }))
    await vi.waitFor(() => expect(wrapper.text()).toContain('Java'))
    await wrapper.get('[data-testid="edit-taxonomy"]').trigger('click')
    await wrapper.get('input[name="editName"]').setValue('Unsaved')
    await wrapper.get('[data-testid="save-taxonomy"]').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain(message))
    expect(wrapper.get<HTMLInputElement>('input[name="editName"]').element.value).toBe('Unsaved')
  })
})
