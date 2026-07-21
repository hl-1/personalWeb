import { describe, expect, it, vi } from 'vitest'
import { SessionApiError, createSessionClient } from './session-client'

type FetchImplementation = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('createSessionClient', () => {
  it('shares an in-memory CSRF request and sends same-origin JSON mutations', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValueOnce(json({ token: 'token-1', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(json({ value: 'saved' }))
    const client = createSessionClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await Promise.all([client.getCsrfToken(), client.getCsrfToken()])
    const result = await client.request('/v1/admin/articles', {
      method: 'POST',
      body: { title: 'Saved' },
      parse: (input) => input as { value: string },
    })

    expect(result.data).toEqual({ value: 'saved' })
    expect(fetchImplementation).toHaveBeenNthCalledWith(2, '/api/v1/admin/articles', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
        'X-CSRF-TOKEN': 'token-1',
      },
      body: JSON.stringify({ title: 'Saved' }),
    })
    expect(fetchImplementation).toHaveBeenCalledTimes(2)
  })

  it('clears a rejected token and retries a csrf_failed mutation exactly once', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValueOnce(json({ token: 'stale', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(json({
        type: 'urn:studystack:problem:csrf-failed', title: 'CSRF failed', status: 403,
        detail: 'Rejected', instance: '/api/v1/admin/articles', code: 'csrf_failed',
      }, 403))
      .mockResolvedValueOnce(json({ token: 'fresh', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    const client = createSessionClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await expect(client.request('/v1/admin/articles/id?version=0', {
      method: 'DELETE', expectedStatus: 204,
    })).resolves.toMatchObject({ data: undefined })
    expect(fetchImplementation).toHaveBeenCalledTimes(4)
  })

  it.each([
    [401, 'unauthorized'],
    [403, 'forbidden'],
    [409, 'conflict'],
  ] as const)('maps %i problems without retaining sensitive bodies', async (status, kind) => {
    const fetchImplementation = vi.fn<FetchImplementation>().mockResolvedValue(json({
      type: 'urn:studystack:problem:test', title: 'Failure', status,
      detail: 'SECRET_DETAIL', instance: '/api/v1/admin/articles',
      code: status === 409 ? 'stale_version' : kind,
    }, status))
    const client = createSessionClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    const error = await client.request('/v1/admin/articles', { method: 'GET', parse: (input) => input })
      .catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(SessionApiError)
    expect(error).toMatchObject({ kind, status })
    expect(String(error)).not.toContain('SECRET_DETAIL')
  })

  it('rejects absolute API hosts', () => {
    expect(() => createSessionClient({ apiBaseUrl: 'https://api.example.com' }))
      .toThrow()
  })
})
