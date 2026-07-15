import { QueryClient, useQueryClient } from '@tanstack/vue-query'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import { createStudyStackApp } from './create-app'

describe('createStudyStackApp', () => {
  const publicEnv = { VITE_API_BASE_URL: '/api' }

  it('installs Router, QueryClient, and Pinia', () => {
    const queryClient = new QueryClient()
    const result = createStudyStackApp({
      history: createMemoryHistory(),
      publicEnv,
      queryClient,
    })

    expect(result.app.config.globalProperties.$router).toBe(result.router)
    expect(result.app.config.globalProperties.$pinia).toBe(result.pinia)
    expect(result.app.runWithContext(() => useQueryClient())).toBe(queryClient)
  })

  it('creates isolated application dependencies', () => {
    const first = createStudyStackApp({ history: createMemoryHistory(), publicEnv })
    const second = createStudyStackApp({ history: createMemoryHistory(), publicEnv })

    expect(first.app).not.toBe(second.app)
    expect(first.router).not.toBe(second.router)
    expect(first.queryClient).not.toBe(second.queryClient)
    expect(first.pinia).not.toBe(second.pinia)
  })

  it('validates public environment before creating the app', () => {
    expect(() =>
      createStudyStackApp({ history: createMemoryHistory(), publicEnv: {} }),
    ).toThrowError(/VITE_API_BASE_URL/)
  })
})
