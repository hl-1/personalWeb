import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { RouterView, createMemoryHistory } from 'vue-router'
import type { AuthState } from '../features/auth/auth-schema'
import { createStudyStackRouter, type AuthRouteAccess } from './index'

const RouteHost = {
  components: { RouterView },
  template: '<RouterView />',
}

const anonymousState: AuthState = { authenticated: false, user: null }

function authenticatedState(roles: ('USER' | 'ADMIN')[]): AuthState {
  return {
    authenticated: true,
    user: {
      id: '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322',
      login: 'octocat',
      displayName: 'The Octocat',
      avatarUrl: null,
      roles,
    },
  }
}

function routeAccess(state: AuthState, returnTo = '/'): AuthRouteAccess {
  return {
    current: vi.fn().mockResolvedValue(state),
    refresh: vi.fn().mockResolvedValue(state),
    remember: vi.fn(),
    consume: vi.fn().mockReturnValue(returnTo),
  }
}

describe('StudyStack routes', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
  })

  it.each([
    ['/', 'home-view', 'StudyStack'],
    ['/about', 'about-view', 'About'],
    ['/blog', 'blog-list-view', 'Blog'],
    ['/blog/public-article', 'article-detail-view', 'article'],
    ['/projects', 'project-list-view', 'Projects'],
    ['/projects/public-project', 'project-detail-view', 'Project'],
    ['/foundation', 'foundation-view', 'StudyStack foundation'],
    ['/unknown-page', 'not-found-view', 'Page not found'],
  ])('renders %s with a stable marker', async (path, marker, text) => {
    const router = createStudyStackRouter(createMemoryHistory(), routeAccess(anonymousState))
    await router.push(path)
    await router.isReady()

    wrapper = mount(RouteHost, {
      global: { plugins: [router, [VueQueryPlugin, { queryClient: new QueryClient() }]] },
    })

    expect(wrapper.get(`[data-testid="${marker}"]`).text()).toContain(text)
  })

  it.each([
    ['/login', 'login-view', 'Sign in'],
    ['/forbidden', 'forbidden-view', 'Access denied'],
  ])('renders the identity route %s', async (path, marker, text) => {
    const router = createStudyStackRouter(createMemoryHistory(), routeAccess(anonymousState))
    await router.push(path)
    await router.isReady()

    wrapper = mount(RouteHost, {
      global: { plugins: [router, [VueQueryPlugin, { queryClient: new QueryClient() }]] },
    })

    expect(wrapper.get(`[data-testid="${marker}"]`).text()).toContain(text)
  })

  it('sends an anonymous admin visitor to login and remembers the path', async () => {
    const access = routeAccess(anonymousState)
    const router = createStudyStackRouter(createMemoryHistory(), access)

    await router.push('/admin')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('login')
    expect(access.remember).toHaveBeenCalledWith('/admin')
  })

  it('sends an authenticated non-admin user to forbidden', async () => {
    const router = createStudyStackRouter(
      createMemoryHistory(),
      routeAccess(authenticatedState(['USER'])),
    )

    await router.push('/admin')
    await router.isReady()

    expect(router.currentRoute.value.name).toBe('forbidden')
  })

  it('allows an authenticated admin to enter the admin dashboard', async () => {
    const router = createStudyStackRouter(
      createMemoryHistory(),
      routeAccess(authenticatedState(['USER', 'ADMIN'])),
    )

    await router.push('/admin')
    await router.isReady()

    wrapper = mount(RouteHost, {
      global: { plugins: [router, [VueQueryPlugin, { queryClient: new QueryClient() }]] },
    })
    expect(router.currentRoute.value.name).toBe('admin-dashboard')
    expect(wrapper.get('[data-testid="admin-dashboard-view"]').text()).toContain('Administration')
  })

  it('refreshes auth state after login success and consumes the saved path', async () => {
    const access = routeAccess(authenticatedState(['USER', 'ADMIN']), '/admin')
    const router = createStudyStackRouter(createMemoryHistory(), access)

    await router.push('/login?status=success')
    await router.isReady()

    expect(access.refresh).toHaveBeenCalledTimes(1)
    expect(access.consume).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.fullPath).toBe('/admin')
  })
})
