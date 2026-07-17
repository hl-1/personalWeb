import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import {
  ContentApiError,
  createContentClient,
  type ContentClient,
} from './content-client'
import {
  articleDetailQueryKey,
  articleListQueryKey,
  createArticleDetailQueryOptions,
  createArticleListQueryOptions,
} from './content-query'

type FetchImplementation = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

const page = {
  items: [],
  page: 2,
  size: 20,
  totalElements: 0,
  totalPages: 0,
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

describe('content client', () => {
  it('encodes pagination and optional taxonomy filters in a same-origin request', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse(page))
    const client = createContentClient({ apiBaseUrl: '/api/', fetch: fetchImplementation })

    await expect(client.getArticles({
      page: 2,
      size: 20,
      category: 'java',
      tag: 'spring',
    })).resolves.toEqual(page)
    expect(fetchImplementation).toHaveBeenCalledWith(
      '/api/v1/articles?page=2&size=20&category=java&tag=spring',
      { headers: { Accept: 'application/json' }, credentials: 'same-origin' },
    )
  })

  it('omits absent filters and rejects arbitrary sort input at runtime', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse({
      ...page,
      page: 0,
      size: 10,
    }))
    const client = createContentClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await client.getArticles()
    expect(fetchImplementation).toHaveBeenCalledWith(
      '/api/v1/articles?page=0&size=10',
      expect.any(Object),
    )
    await expect(Reflect.apply(client.getArticles, client, [{ sort: 'title' }]))
      .rejects.toThrowError()
  })

  it('maps a 404 ProblemDetail without retaining its detail or unknown response', async () => {
    const secret = 'TOP_SECRET_UNPUBLISHED_TITLE'
    const fetchImplementation = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse({
      type: 'urn:studystack:problem:article-not-found',
      title: 'Article not found',
      status: 404,
      detail: secret,
      instance: '/api/v1/articles/missing',
      code: 'article_not_found',
    }, 404))
    const client = createContentClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    const error = await client.getArticle('missing').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ContentApiError)
    expect(error).toMatchObject({ kind: 'not_found', status: 404, code: 'article_not_found' })
    expect(String(error)).not.toContain(secret)
    expect(error).not.toHaveProperty('body')
    expect(error).not.toHaveProperty('response')
  })

  it.each(['https://example.com/api', '//example.com/api'])(
    'rejects non-same-origin API base %s',
    (apiBaseUrl) => {
      expect(() => createContentClient({ apiBaseUrl })).toThrowError(/VITE_API_BASE_URL/)
    },
  )
})

describe('content query options', () => {
  function client(): ContentClient {
    return {
      getArticles: vi.fn().mockResolvedValue(page),
      getArticle: vi.fn(),
      getCategories: vi.fn(),
      getTags: vi.fn(),
    }
  }

  it('isolates list caches by page, size, category, and tag', () => {
    expect(articleListQueryKey({ page: 1, size: 20, category: 'java', tag: 'spring' }))
      .not.toEqual(articleListQueryKey({ page: 2, size: 20, category: 'java', tag: 'spring' }))
    expect(articleListQueryKey({ page: 1, size: 20, category: 'java', tag: 'spring' }))
      .not.toEqual(articleListQueryKey({ page: 1, size: 20, category: 'java', tag: 'database' }))
  })

  it('isolates detail caches by slug and stores fetched pages in TanStack Query', async () => {
    expect(articleDetailQueryKey('first-article')).not.toEqual(articleDetailQueryKey('second-article'))
    const contentClient = client()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const options = createArticleListQueryOptions(contentClient, { page: 2, size: 20 })

    await queryClient.fetchQuery(options)

    expect(queryClient.getQueryData(articleListQueryKey({ page: 2, size: 20 }))).toEqual(page)
    expect(createArticleDetailQueryOptions(contentClient, 'public-article').retry).toBe(false)
  })
})
