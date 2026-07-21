import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { AdminApiError, type AdminClient } from '../admin-client'
import AdminProjectListView from '../../../views/admin/AdminProjectListView.vue'
import AdminProjectEditView from '../../../views/admin/AdminProjectEditView.vue'

const id = '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'
const instant = '2026-07-20T10:00:00Z'
const project = { id, slug: 'project', title: 'Project', summary: 'Summary', descriptionMarkdown: '',
  projectUrl: 'https://example.com', repositoryUrl: null, status: 'DRAFT' as const,
  featured: false, sortOrder: 0, publishedAt: null, createdAt: instant, updatedAt: instant, version: 0 }

function client(overrides: Partial<AdminClient> = {}): AdminClient {
  return { listProjects: vi.fn().mockResolvedValue({ items: [project], page: 0, size: 20,
    totalElements: 1, totalPages: 1 }), getProject: vi.fn().mockResolvedValue(project),
  createProject: vi.fn().mockResolvedValue({ data: project, location: `/api/v1/admin/portfolio/projects/${id}` }),
  updateProject: vi.fn().mockResolvedValue(project), deleteProject: vi.fn(),
  publishProject: vi.fn().mockResolvedValue({ ...project, status: 'PUBLISHED' }),
  archiveProject: vi.fn().mockResolvedValue({ ...project, status: 'ARCHIVED' }),
  previewProject: vi.fn().mockResolvedValue({ html: '<p>Preview</p>' }), ...overrides } as AdminClient
}

async function plugins(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes: [
    { path: '/admin/portfolio/projects', component: AdminProjectListView },
    { path: '/admin/portfolio/projects/new', component: AdminProjectEditView },
    { path: '/admin/portfolio/projects/:id', component: AdminProjectEditView },
  ] })
  await router.push(path); await router.isReady()
  return { router, queryClient: new QueryClient(), pinia: createPinia() }
}

describe('project administration views', () => {
  it('renders draft projects with publish and delete actions', async () => {
    const p = await plugins('/admin/portfolio/projects')
    const wrapper = mount(AdminProjectListView, { props: { adminClient: client() },
      global: { plugins: [p.router, [VueQueryPlugin, { queryClient: p.queryClient }], p.pinia] } })
    await vi.waitFor(() => expect(wrapper.find('[data-testid="project-list"]').exists()).toBe(true))
    expect(wrapper.find('[data-testid="publish-project"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="archive-project"]').exists()).toBe(false)
  })

  it('preserves long input after stale_version', async () => {
    const updateProject = vi.fn().mockRejectedValue(new AdminApiError('conflict', 409, 'stale_version'))
    const p = await plugins(`/admin/portfolio/projects/${id}`)
    const wrapper = mount(AdminProjectEditView, { props: { adminClient: client({ updateProject }), id },
      global: { plugins: [p.router, [VueQueryPlugin, { queryClient: p.queryClient }], p.pinia] } })
    await vi.waitFor(() => expect(wrapper.get('input[name="title"]').element).toHaveProperty('value', 'Project'))
    const longTitle = 'A project title that remains visible and does not overflow'
    await wrapper.get('input[name="title"]').setValue(longTitle)
    await wrapper.get('[data-testid="save-project"]').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('Reload server version'))
    expect(wrapper.get<HTMLInputElement>('input[name="title"]').element.value).toBe(longTitle)
  })
})
