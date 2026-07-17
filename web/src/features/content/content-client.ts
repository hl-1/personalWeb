import {
  getPublicJson,
  normalizeApiBaseUrl,
  type PublicApiErrorKind,
  type PublicApiFailure,
} from '../../shared/api/public-api-client'
import {
  parseArticleDetail,
  parseArticleListParams,
  parseArticlePage,
  parseArticleSlug,
  parseTaxonomies,
  type ArticleDetail,
  type ArticleListParams,
  type ArticlePage,
  type Taxonomy,
} from './content-schema'

const errorMessages: Record<PublicApiErrorKind, string> = {
  network: 'Content request failed',
  invalid_request: 'Content request was invalid',
  not_found: 'Content resource was not found',
  response_error: 'Content request returned an error',
  invalid_response: 'Content response was invalid',
}

export class ContentApiError extends Error {
  readonly kind: PublicApiErrorKind
  readonly status: number | undefined
  readonly code: string | undefined

  constructor(failure: PublicApiFailure) {
    super(errorMessages[failure.kind])
    this.name = 'ContentApiError'
    this.kind = failure.kind
    this.status = failure.status
    this.code = failure.code
  }
}

export interface ContentClient {
  getArticles(params?: ArticleListParams): Promise<ArticlePage>
  getArticle(slug: string): Promise<ArticleDetail>
  getCategories(): Promise<Taxonomy[]>
  getTags(): Promise<Taxonomy[]>
}

export interface ContentClientOptions {
  apiBaseUrl: string
  fetch?: typeof globalThis.fetch
}

export function createContentClient(options: ContentClientOptions): ContentClient {
  const apiBaseUrl = normalizeApiBaseUrl(options.apiBaseUrl)
  const fetchImplementation = options.fetch ?? globalThis.fetch
  const createError = (failure: PublicApiFailure) => new ContentApiError(failure)

  return {
    async getArticles(input: ArticleListParams = {}): Promise<ArticlePage> {
      const params = parseArticleListParams(input)
      const query = new URLSearchParams({
        page: String(params.page),
        size: String(params.size),
      })
      if (params.category !== undefined) {
        query.set('category', params.category)
      }
      if (params.tag !== undefined) {
        query.set('tag', params.tag)
      }
      return getPublicJson(
        `${apiBaseUrl}/v1/articles?${query}`,
        fetchImplementation,
        parseArticlePage,
        createError,
      )
    },

    async getArticle(input: string): Promise<ArticleDetail> {
      const slug = parseArticleSlug(input)
      return getPublicJson(
        `${apiBaseUrl}/v1/articles/${encodeURIComponent(slug)}`,
        fetchImplementation,
        parseArticleDetail,
        createError,
      )
    },

    getCategories(): Promise<Taxonomy[]> {
      return getPublicJson(
        `${apiBaseUrl}/v1/categories`,
        fetchImplementation,
        parseTaxonomies,
        createError,
      )
    },

    getTags(): Promise<Taxonomy[]> {
      return getPublicJson(
        `${apiBaseUrl}/v1/tags`,
        fetchImplementation,
        parseTaxonomies,
        createError,
      )
    },
  }
}
