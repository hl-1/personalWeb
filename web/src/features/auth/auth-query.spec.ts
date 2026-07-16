import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import type { AuthClient } from './auth-client'
import {
  authQueryKey,
  createAuthQueryOptions,
  logoutWithQueryClient,
} from './auth-query'

const anonymousState = { authenticated: false, user: null } as const

function createClient(): AuthClient {
  return {
    getAuthState: vi.fn().mockResolvedValue(anonymousState),
    getCsrfToken: vi.fn(),
    logout: vi.fn().mockResolvedValue(undefined),
  }
}

describe('auth query', () => {
  it('stores current auth state under the fixed TanStack Query key', async () => {
    const client = createClient()
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })

    const state = await queryClient.fetchQuery(createAuthQueryOptions(client))

    expect(state).toEqual(anonymousState)
    expect(queryClient.getQueryData(authQueryKey)).toEqual(anonymousState)
    expect(client.getAuthState).toHaveBeenCalledTimes(1)
  })

  it('disables automatic auth query retries', () => {
    const options = createAuthQueryOptions(createClient())

    expect(options.retry).toBe(false)
  })

  it('removes auth state from Query cache after logout', async () => {
    const client = createClient()
    const queryClient = new QueryClient()
    queryClient.setQueryData(authQueryKey, anonymousState)

    await logoutWithQueryClient(client, queryClient)

    expect(client.logout).toHaveBeenCalledTimes(1)
    expect(queryClient.getQueryData(authQueryKey)).toBeUndefined()
  })

  it('keeps cached auth state when logout fails', async () => {
    const client = createClient()
    vi.mocked(client.logout).mockRejectedValue(new Error('logout failed'))
    const queryClient = new QueryClient()
    queryClient.setQueryData(authQueryKey, anonymousState)

    await expect(logoutWithQueryClient(client, queryClient)).rejects.toThrow('logout failed')

    expect(queryClient.getQueryData(authQueryKey)).toEqual(anonymousState)
  })
})
