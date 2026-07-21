import { describe, expect, it, vi } from 'vitest'
import { createAdminClient } from './admin-client'

type FetchImplementation = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>
const id = '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'
const instant = '2026-07-20T10:00:00Z'
const detail = { id, slug: 'article', title: 'Title', summary: 'Summary', bodyMarkdown: '',
  status: 'DRAFT', categoryId: null, tagIds: [], seoTitle: null, seoDescription: null,
  publishedAt: null, createdAt: instant, updatedAt: instant, version: 0 }

function json(body: unknown, status = 200, headers?: HeadersInit): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
}

describe('createAdminClient', () => {
  it('encodes article list/detail queries through fixed relative paths', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValueOnce(json({ items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 }))
      .mockResolvedValueOnce(json(detail))
    const client = createAdminClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await client.listArticles({ page: 1, size: 20, status: 'DRAFT', query: 'a & b' })
    await client.getArticle(id)

    expect(fetchImplementation.mock.calls[0]?.[0]).toBe('/api/v1/admin/articles?page=1&size=20&status=DRAFT&query=a+%26+b')
    expect(fetchImplementation.mock.calls[1]?.[0]).toBe(`/api/v1/admin/articles/${id}`)
  })

  it('uses fixed mutation, delete, preview and Location contracts', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>()
      .mockResolvedValueOnce(json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }))
      .mockResolvedValueOnce(json(detail, 201, { Location: `/api/v1/admin/articles/${id}` }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json({ html: '<p>Safe</p>' }))
    const client = createAdminClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    const created = await client.createArticle({ slug: 'article', title: 'Title', summary: 'Summary',
      bodyMarkdown: '', categoryId: null, tagIds: [], seoTitle: null, seoDescription: null })
    await client.deleteArticle(id, 0)
    await client.previewArticle('# Preview')

    expect(created.location).toBe(`/api/v1/admin/articles/${id}`)
    expect(fetchImplementation.mock.calls[2]?.[0]).toBe(`/api/v1/admin/articles/${id}?version=0`)
    expect(fetchImplementation.mock.calls[3]?.[0]).toBe('/api/v1/admin/articles/preview')
  })
})
