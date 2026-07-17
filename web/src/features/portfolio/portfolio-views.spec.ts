import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import HomeView from '../../views/HomeView.vue'
import AboutView from '../../views/AboutView.vue'
import ProjectDetailView from '../../views/ProjectDetailView.vue'
import ProjectListView from '../../views/ProjectListView.vue'
import { parseSafeHtml } from '../../shared/content/safe-html'
import type { ContentClient } from '../content/content-client'
import { PortfolioApiError, type PortfolioClient } from './portfolio-client'

const project = {
  id: '00000000-0000-4000-8000-000000000010',
  slug: 'public-project',
  title: 'Public project',
  summary: 'Project summary',
  featured: true,
  publishedAt: '2026-07-17T06:00:00Z',
  updatedAt: '2026-07-17T07:00:00Z',
}

function portfolioClient(overrides: Partial<PortfolioClient> = {}): PortfolioClient {
  return {
    getProfile: vi.fn().mockResolvedValue({
      displayName: 'StudyStack Author',
      headline: 'Platform engineer',
      bioHtml: parseSafeHtml('<p>Public biography</p>'),
      seoDescription: 'Profile description',
    }),
    getProjects: vi.fn().mockResolvedValue({
      items: [project], page: 0, size: 10, totalElements: 1, totalPages: 1,
    }),
    getProject: vi.fn().mockImplementation(async (slug: string) => ({
      ...project,
      slug,
      title: `Project ${slug}`,
      descriptionHtml: parseSafeHtml('<p>Project detail</p>'),
      projectUrl: 'https://example.com/project',
      repositoryUrl: null,
      canonicalPath: `/projects/${slug}`,
    })),
    getSkills: vi.fn().mockResolvedValue([
      { id: '00000000-0000-4000-8000-000000000011', name: 'Java', category: 'Backend', summary: null },
    ]),
    getExperiences: vi.fn().mockResolvedValue([
      {
        id: '00000000-0000-4000-8000-000000000012',
        organization: 'StudyStack',
        role: 'Engineer',
        startDate: '2024-01-01',
        endDate: null,
        summaryHtml: parseSafeHtml('<p>Built reliable systems</p>'),
      },
    ]),
    ...overrides,
  }
}

function contentClient(): ContentClient {
  return {
    getArticles: vi.fn().mockResolvedValue({
      items: [], page: 0, size: 3, totalElements: 0, totalPages: 0,
    }),
    getArticle: vi.fn(),
    getCategories: vi.fn(),
    getTags: vi.fn(),
  }
}

describe('HomeView and AboutView', () => {
  let wrapper: VueWrapper | undefined
  afterEach(() => wrapper?.unmount())

  it('keeps site identity and explicit empty summaries when all public data is empty', async () => {
    const portfolio = portfolioClient({
      getProfile: vi.fn().mockRejectedValue(new PortfolioApiError({ kind: 'not_found', status: 404 })),
      getProjects: vi.fn().mockResolvedValue({
        items: [], page: 0, size: 3, totalElements: 0, totalPages: 0,
      }),
    })
    wrapper = await mountView(HomeView, {
      contentClient: contentClient(),
      portfolioClient: portfolio,
    }, '/')
    await flushPromises()

    expect(wrapper.get('h1').text()).toContain('StudyStack')
    expect(wrapper.find('[data-testid="home-profile-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-articles-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="home-projects-empty"]').exists()).toBe(true)
  })

  it('renders profile, skills, and experience with safe markdown', async () => {
    wrapper = await mountView(AboutView, { portfolioClient: portfolioClient() }, '/about')
    await flushPromises()

    expect(wrapper.text()).toContain('StudyStack Author')
    expect(wrapper.text()).toContain('Java')
    expect(wrapper.text()).toContain('StudyStack')
    expect(wrapper.html()).toContain('Built reliable systems')
  })

  it('renders a missing profile as an empty About state', async () => {
    wrapper = await mountView(AboutView, {
      portfolioClient: portfolioClient({
        getProfile: vi.fn().mockRejectedValue(new PortfolioApiError({
          kind: 'not_found', status: 404,
        })),
      }),
    }, '/about')
    await flushPromises()

    expect(wrapper.get('[data-testid="about-profile-empty"]').text())
      .toContain('Public profile coming soon')
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })
})

describe('ProjectListView and ProjectDetailView', () => {
  it('renders invalid project slugs as not found without requesting the API', async () => {
    const client = portfolioClient()
    const wrapper = await mountView(
      ProjectDetailView,
      { portfolioClient: client, slug: 'ab' },
      '/projects/ab',
    )
    await flushPromises()

    expect(wrapper.get('[data-testid="not-found-state"]').text()).toContain('Project not found')
    expect(client.getProject).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('renders empty/error list states and pagination boundaries', async () => {
    const empty = portfolioClient({
      getProjects: vi.fn().mockResolvedValue({
        items: [], page: 0, size: 10, totalElements: 0, totalPages: 0,
      }),
    })
    let wrapper = await mountView(ProjectListView, { portfolioClient: empty }, '/projects')
    await flushPromises()
    expect(wrapper.get('[data-testid="empty-state"]').text()).toContain('No published projects')
    expect(wrapper.find('[data-testid="previous-page"]').exists()).toBe(false)
    wrapper.unmount()

    const singlePage = portfolioClient()
    wrapper = await mountView(ProjectListView, { portfolioClient: singlePage }, '/projects')
    await flushPromises()
    expect(wrapper.get('[data-testid="previous-page"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-testid="next-page"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()

    const failed = portfolioClient({ getProjects: vi.fn().mockRejectedValue(new Error('failed')) })
    wrapper = await mountView(ProjectListView, { portfolioClient: failed }, '/projects')
    await flushPromises()
    expect(wrapper.get('[role="alert"]').text()).toContain('could not be loaded')
    wrapper.unmount()
  })

  it('switches project slugs and renders stable 404 state', async () => {
    const client = portfolioClient()
    let wrapper = await mountView(
      ProjectDetailView,
      { portfolioClient: client, slug: 'first-project' },
      '/projects/first-project',
    )
    await flushPromises()
    await wrapper.setProps({ slug: 'second-project' })
    await flushPromises()
    expect(wrapper.text()).toContain('Project second-project')
    wrapper.unmount()

    const missing = portfolioClient({
      getProject: vi.fn().mockRejectedValue(new PortfolioApiError({
        kind: 'not_found', status: 404, code: 'portfolio_not_found',
      })),
    })
    wrapper = await mountView(
      ProjectDetailView,
      { portfolioClient: missing, slug: 'missing-project' },
      '/projects/missing-project',
    )
    await flushPromises()
    expect(wrapper.get('[data-testid="not-found-state"]').text()).toContain('Project not found')
    expect(wrapper.get('[data-testid="not-found-state"] h1').text()).toBe('Project not found')
    expect(wrapper.text()).not.toContain('portfolio_not_found')
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
