import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import AdminLayout from '../../layouts/AdminLayout.vue'
import AdminPageState from './components/AdminPageState.vue'

async function routerAt(path = '/admin/articles') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/admin/:pathMatch(.*)*', component: { template: '<div />' } }],
  })
  await router.push(path)
  await router.isReady()
  return router
}

describe('AdminLayout', () => {
  it('renders compact navigation, a current item and an exit entry', async () => {
    const wrapper = mount(AdminLayout, {
      global: { plugins: [await routerAt()] },
      slots: { default: '<h1>A very long administrative page heading that wraps safely</h1>' },
    })
    expect(wrapper.get('[data-testid="admin-layout"]').text()).toContain('Administration')
    expect(wrapper.findAll('[data-testid="admin-nav-link"]')).toHaveLength(8)
    expect(wrapper.get('[data-testid="admin-exit-link"]').attributes('href')).toBe('/')
  })
})

describe('AdminPageState', () => {
  it.each([
    ['loading', 'Loading'],
    ['empty', 'No records'],
    ['error', 'Try again'],
    ['unauthorized', 'Sign in'],
    ['forbidden', 'Access denied'],
  ] as const)('renders the %s state', async (state, marker) => {
    const wrapper = mount(AdminPageState, {
      props: { state },
      global: { plugins: [await routerAt()] },
    })
    expect(wrapper.text()).toContain(marker)
  })

  it('renders ready content without wrapping it in a card', async () => {
    const wrapper = mount(AdminPageState, {
      props: { state: 'ready' },
      slots: { default: '<div data-testid="ready-content">Ready</div>' },
      global: { plugins: [await routerAt()] },
    })
    expect(wrapper.get('[data-testid="ready-content"]').text()).toBe('Ready')
  })
})
