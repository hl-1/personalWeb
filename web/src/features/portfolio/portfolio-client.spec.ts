import { QueryClient } from '@tanstack/vue-query'
import { describe, expect, it, vi } from 'vitest'
import {
  createPortfolioClient,
  PortfolioApiError,
  type PortfolioClient,
} from './portfolio-client'
import {
  createProjectListQueryOptions,
  projectDetailQueryKey,
  projectListQueryKey,
} from './portfolio-query'

type FetchImplementation = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

const page = {
  items: [],
  page: 1,
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

describe('portfolio client', () => {
  it('encodes project pagination and the optional featured filter', async () => {
    const fetchImplementation = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse(page))
    const client = createPortfolioClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    await expect(client.getProjects({ page: 1, size: 20, featured: true })).resolves.toEqual(page)
    expect(fetchImplementation).toHaveBeenCalledWith(
      '/api/v1/portfolio/projects?page=1&size=20&featured=true',
      { headers: { Accept: 'application/json' }, credentials: 'same-origin' },
    )
  })

  it('uses fixed same-origin paths and rejects arbitrary project sorting', async () => {
    const profile = {
      displayName: 'Author',
      headline: 'Engineer',
      bioHtml: '<p>Safe</p>',
      seoDescription: null,
    }
    const fetchImplementation = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse(profile))
    const client = createPortfolioClient({ apiBaseUrl: '/api/', fetch: fetchImplementation })

    await client.getProfile()
    expect(fetchImplementation).toHaveBeenCalledWith(
      '/api/v1/portfolio/profile',
      expect.any(Object),
    )
    await expect(Reflect.apply(client.getProjects, client, [{ sort: 'title' }]))
      .rejects.toThrowError()
  })

  it('maps portfolio 404 ProblemDetail without retaining unpublished details', async () => {
    const secret = 'TOP_SECRET_DRAFT_PROJECT'
    const fetchImplementation = vi.fn<FetchImplementation>().mockResolvedValue(jsonResponse({
      type: 'urn:studystack:problem:portfolio-not-found',
      title: 'Portfolio resource not found',
      status: 404,
      detail: secret,
      instance: '/api/v1/portfolio/projects/missing',
      code: 'portfolio_not_found',
    }, 404))
    const client = createPortfolioClient({ apiBaseUrl: '/api', fetch: fetchImplementation })

    const error = await client.getProject('missing').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(PortfolioApiError)
    expect(error).toMatchObject({ kind: 'not_found', status: 404, code: 'portfolio_not_found' })
    expect(String(error)).not.toContain(secret)
    expect(error).not.toHaveProperty('body')
  })
})

describe('portfolio query options', () => {
  function client(): PortfolioClient {
    return {
      getProfile: vi.fn(),
      getProjects: vi.fn().mockResolvedValue(page),
      getProject: vi.fn(),
      getSkills: vi.fn(),
      getExperiences: vi.fn(),
    }
  }

  it('isolates project list caches by page, size, and featured filter', () => {
    expect(projectListQueryKey({ page: 0, size: 10, featured: true }))
      .not.toEqual(projectListQueryKey({ page: 0, size: 10, featured: false }))
    expect(projectListQueryKey({ page: 0, size: 10 }))
      .not.toEqual(projectListQueryKey({ page: 1, size: 10 }))
  })

  it('isolates details by slug and stores pages only in TanStack Query', async () => {
    expect(projectDetailQueryKey('first-project')).not.toEqual(projectDetailQueryKey('second-project'))
    const portfolioClient = client()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const options = createProjectListQueryOptions(portfolioClient, { page: 1, size: 20 })

    await queryClient.fetchQuery(options)

    expect(queryClient.getQueryData(projectListQueryKey({ page: 1, size: 20 }))).toEqual(page)
  })
})
