import { describe, expect, it, vi } from 'vitest'
import { AuthApiError, createAuthClient } from './auth-client'

type FetchImplementation = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

const anonymousResponse = { authenticated: false, user: null }
const csrfResponse = { token: 'csrf-token', headerName: 'X-CSRF-TOKEN' }

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('createAuthClient', () => {
  it('loads auth state through a relative same-origin request', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValue(jsonResponse(anonymousResponse))
    const client = createAuthClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await expect(client.getAuthState()).resolves.toEqual(anonymousResponse)
    expect(fetchImplementation).toHaveBeenCalledWith('/api/v1/auth/me', {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
    })
  })

  it('caches the CSRF token in memory', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValue(jsonResponse(csrfResponse))
    const client = createAuthClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await expect(client.getCsrfToken()).resolves.toEqual(csrfResponse)
    await expect(client.getCsrfToken()).resolves.toEqual(csrfResponse)
    expect(fetchImplementation).toHaveBeenCalledTimes(1)
  })

  it('fetches a CSRF token before logout and clears it after success', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
    const client = createAuthClient({ apiBaseUrl: '/api/', fetch: fetchImplementation })

    await expect(client.logout()).resolves.toBeUndefined()
    await client.getCsrfToken()

    expect(fetchImplementation).toHaveBeenNthCalledWith(2, '/api/v1/auth/logout', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'csrf-token',
      },
      credentials: 'same-origin',
    })
    expect(fetchImplementation).toHaveBeenCalledTimes(3)
  })

  it('clears a rejected CSRF token and does not retry a forbidden logout', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValueOnce(jsonResponse(csrfResponse))
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
      .mockResolvedValueOnce(jsonResponse({ token: 'fresh-token', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const client = createAuthClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await expect(client.logout()).rejects.toMatchObject({ kind: 'forbidden', status: 403 })
    expect(fetchImplementation).toHaveBeenCalledTimes(2)

    await expect(client.logout()).resolves.toBeUndefined()
    expect(fetchImplementation).toHaveBeenCalledTimes(4)
    expect(fetchImplementation).toHaveBeenNthCalledWith(4, '/api/v1/auth/logout', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'X-CSRF-TOKEN': 'fresh-token',
      },
      credentials: 'same-origin',
    })
  })

  it.each([
    [401, 'unauthorized'],
    [403, 'forbidden'],
  ] as const)('maps HTTP %i to a fixed %s error', async (status, kind) => {
    const secret = 'TOP_SECRET_RESPONSE_VALUE'
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValue(jsonResponse({ secret }, status))
    const client = createAuthClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    const error = await client.getAuthState().catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(AuthApiError)
    expect(error).toMatchObject({ kind, status })
    expect(String(error)).not.toContain(secret)
    expect(error).not.toHaveProperty('response')
    expect(error).not.toHaveProperty('body')
  })

  it('maps network failures without retaining their sensitive cause', async () => {
    const secret = 'TOP_SECRET_NETWORK_VALUE'
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockRejectedValue(new Error(secret))
    const client = createAuthClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    const error = await client.getAuthState().catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(AuthApiError)
    expect(error).toMatchObject({ kind: 'network' })
    expect(String(error)).not.toContain(secret)
    expect(error).not.toHaveProperty('cause')
  })

  it('maps invalid JSON responses without retaining their content', async () => {
    const secret = 'TOP_SECRET_INVALID_RESPONSE'
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValue(new Response(secret, { status: 200 }))
    const client = createAuthClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    const error = await client.getAuthState().catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(AuthApiError)
    expect(error).toMatchObject({ kind: 'invalid_response' })
    expect(String(error)).not.toContain(secret)
  })
})
