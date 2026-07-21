import { describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import type { AuthState } from '../auth/auth-schema'
import { createStudyStackRouter, type AuthRouteAccess } from '../../router'

const admin: AuthState = {
  authenticated: true,
  user: {
    id: '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322', login: 'admin', displayName: 'Admin',
    avatarUrl: null, roles: ['USER', 'ADMIN'],
  },
}

function access(state: AuthState): AuthRouteAccess {
  return {
    current: vi.fn().mockResolvedValue(state),
    refresh: vi.fn().mockResolvedValue(state),
    remember: vi.fn(),
    consume: vi.fn().mockReturnValue('/'),
  }
}

describe('admin routing', () => {
  it.each([
    ['/admin', 'admin-dashboard'],
    ['/admin/articles', 'admin-articles'],
    ['/admin/articles/new', 'admin-article-new'],
    ['/admin/articles/2d65e30a-f450-4f8e-8ed9-5f36b2f7c322', 'admin-article-edit'],
    ['/admin/categories', 'admin-categories'],
    ['/admin/tags', 'admin-tags'],
    ['/admin/portfolio/projects', 'admin-projects'],
    ['/admin/portfolio/profile', 'admin-profile'],
    ['/admin/portfolio/skills', 'admin-skills'],
    ['/admin/portfolio/experiences', 'admin-experiences'],
    ['/admin/missing', 'admin-not-found'],
  ])('supports direct access to %s', async (path, name) => {
    const router = createStudyStackRouter(createMemoryHistory(), access(admin))
    await router.push(path)
    await router.isReady()
    expect(router.currentRoute.value.name).toBe(name)
    expect(router.currentRoute.value.meta.requiresAdmin).toBe(true)
  })

  it('remembers a nested returnTo for anonymous users', async () => {
    const anonymous: AuthState = { authenticated: false, user: null }
    const routeAccess = access(anonymous)
    const router = createStudyStackRouter(createMemoryHistory(), routeAccess)
    await router.push('/admin/articles/new')
    await router.isReady()
    expect(router.currentRoute.value.name).toBe('login')
    expect(routeAccess.remember).toHaveBeenCalledWith('/admin/articles/new')
  })
})
