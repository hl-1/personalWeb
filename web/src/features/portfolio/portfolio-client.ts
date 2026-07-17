import {
  getPublicJson,
  normalizeApiBaseUrl,
  type PublicApiErrorKind,
  type PublicApiFailure,
} from '../../shared/api/public-api-client'
import {
  parseExperiences,
  parsePortfolioProfile,
  parseProjectDetail,
  parseProjectListParams,
  parseProjectPage,
  parseProjectSlug,
  parseSkills,
  type Experience,
  type PortfolioProfile,
  type ProjectDetail,
  type ProjectListParams,
  type ProjectPage,
  type Skill,
} from './portfolio-schema'

const errorMessages: Record<PublicApiErrorKind, string> = {
  network: 'Portfolio request failed',
  invalid_request: 'Portfolio request was invalid',
  not_found: 'Portfolio resource was not found',
  response_error: 'Portfolio request returned an error',
  invalid_response: 'Portfolio response was invalid',
}

export class PortfolioApiError extends Error {
  readonly kind: PublicApiErrorKind
  readonly status: number | undefined
  readonly code: string | undefined

  constructor(failure: PublicApiFailure) {
    super(errorMessages[failure.kind])
    this.name = 'PortfolioApiError'
    this.kind = failure.kind
    this.status = failure.status
    this.code = failure.code
  }
}

export interface PortfolioClient {
  getProfile(): Promise<PortfolioProfile>
  getProjects(params?: ProjectListParams): Promise<ProjectPage>
  getProject(slug: string): Promise<ProjectDetail>
  getSkills(): Promise<Skill[]>
  getExperiences(): Promise<Experience[]>
}

export interface PortfolioClientOptions {
  apiBaseUrl: string
  fetch?: typeof globalThis.fetch
}

export function createPortfolioClient(options: PortfolioClientOptions): PortfolioClient {
  const apiBaseUrl = normalizeApiBaseUrl(options.apiBaseUrl)
  const fetchImplementation = options.fetch ?? globalThis.fetch
  const createError = (failure: PublicApiFailure) => new PortfolioApiError(failure)

  return {
    getProfile(): Promise<PortfolioProfile> {
      return getPublicJson(
        `${apiBaseUrl}/v1/portfolio/profile`,
        fetchImplementation,
        parsePortfolioProfile,
        createError,
      )
    },

    async getProjects(input: ProjectListParams = {}): Promise<ProjectPage> {
      const params = parseProjectListParams(input)
      const query = new URLSearchParams({
        page: String(params.page),
        size: String(params.size),
      })
      if (params.featured !== undefined) {
        query.set('featured', String(params.featured))
      }
      return getPublicJson(
        `${apiBaseUrl}/v1/portfolio/projects?${query}`,
        fetchImplementation,
        parseProjectPage,
        createError,
      )
    },

    async getProject(input: string): Promise<ProjectDetail> {
      const slug = parseProjectSlug(input)
      return getPublicJson(
        `${apiBaseUrl}/v1/portfolio/projects/${encodeURIComponent(slug)}`,
        fetchImplementation,
        parseProjectDetail,
        createError,
      )
    },

    getSkills(): Promise<Skill[]> {
      return getPublicJson(
        `${apiBaseUrl}/v1/portfolio/skills`,
        fetchImplementation,
        parseSkills,
        createError,
      )
    },

    getExperiences(): Promise<Experience[]> {
      return getPublicJson(
        `${apiBaseUrl}/v1/portfolio/experiences`,
        fetchImplementation,
        parseExperiences,
        createError,
      )
    },
  }
}
