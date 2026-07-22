import { VueQueryPlugin, QueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { createPinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import App from '../../App.vue'
import { createStudyStackRouter } from '../../router'
import LoginView from '../../views/LoginView.vue'
import type { AuthClient } from './auth-client'
import type { AuthState } from './auth-schema'

const anonymousState: AuthState = { authenticated: false, user: null }
const adminState: AuthState = {
  authenticated: true,
  user: {
    id: '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322',
    login: 'octocat',
    displayName: 'The Octocat',
    avatarUrl: null,
    roles: ['USER', 'ADMIN'],
  },
}

function authClient(state: AuthState = anonymousState): AuthClient {
  return {
    getAuthState: vi.fn().mockResolvedValue(state),
    getCsrfToken: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
  }
}

describe('LoginView', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => wrapper?.unmount())

  async function mountLogin(query = ''): Promise<VueWrapper> {
    const router = createStudyStackRouter(createMemoryHistory())
    await router.push(`/login${query}`)
    await router.isReady()
    wrapper = mount(LoginView, { global: { plugins: [router] } })
    return wrapper
  }

  it('uses the fixed same-origin GitHub OAuth entry', async () => {
    const login = await mountLogin()

    expect(login.get('[data-testid="github-login"]').attributes('href'))
      .toBe('/oauth2/authorization/github')
  })

  it.each([
    ['oauth_denied', 'GitHub authorization was cancelled.'],
    ['invalid_profile', 'The GitHub profile could not be used.'],
    ['identity_conflict', 'This GitHub identity is already linked.'],
    ['account_disabled', 'This account has been disabled.'],
    ['login_failed', 'Sign-in could not be completed.'],
  ])('maps %s to approved copy', async (code, message) => {
    const login = await mountLogin(`?error=${code}`)

    expect(login.get('[role="alert"]').text()).toBe(message)
  })

  it('uses generic copy without rendering an unknown error value', async () => {
    const secret = 'TOP_SECRET_PROVIDER_MESSAGE'
    const login = await mountLogin(`?error=${secret}`)

    expect(login.get('[role="alert"]').text()).toBe('Sign-in could not be completed.')
    expect(login.text()).not.toContain(secret)
  })
})

describe('authenticated navigation', () => {
  afterEach(() => ElMessage.closeAll())

  it('shows admin navigation and logs out through the auth client', async () => {
    const client = authClient(adminState)
    vi.mocked(client.getAuthState)
      .mockResolvedValueOnce(adminState)
      .mockResolvedValue(anonymousState)
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
    const router = createStudyStackRouter(createMemoryHistory())
    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, {
      props: { authClient: client },
      global: { plugins: [router, [VueQueryPlugin, { queryClient }], createPinia()] },
    })
    await flushPromises()

    expect(wrapper.get('[data-testid="admin-link"]').attributes('href')).toBe('/admin')
    await wrapper.get('[data-testid="logout-button"]').trigger('click')
    await flushPromises()

    expect(client.logout).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-testid="login-link"]').attributes('href')).toBe('/login')
    expect(document.body.querySelector('.el-message--success')?.textContent).toContain('退出登录成功')
    wrapper.unmount()
  })
})
