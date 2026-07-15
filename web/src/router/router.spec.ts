import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import App from '../App.vue'
import { createStudyStackRouter } from './index'

describe('StudyStack routes', () => {
  let wrapper: VueWrapper | undefined

  afterEach(() => {
    wrapper?.unmount()
  })

  it.each([
    ['/', 'home-view', 'StudyStack'],
    ['/foundation', 'foundation-view', 'StudyStack foundation'],
    ['/unknown-page', 'not-found-view', 'Page not found'],
  ])('renders %s with a stable marker', async (path, marker, text) => {
    const router = createStudyStackRouter(createMemoryHistory())
    await router.push(path)
    await router.isReady()

    wrapper = mount(App, { global: { plugins: [router] } })

    expect(wrapper.get(`[data-testid="${marker}"]`).text()).toContain(text)
  })
})
