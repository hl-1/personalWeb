import { z } from 'zod'
import { createPageSchema, pageNumberSchema, pageSizeSchema } from '../../shared/api/page-schema'
import { publicSlugSchema } from '../../shared/api/slug-schema'
import { safeHtmlSchema, type SafeHtml } from '../../shared/content/safe-html'

const instantSchema = z.string().regex(
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/,
  'must be a UTC ISO-8601 instant',
)
const canonicalPathSchema = z.string().startsWith('/').refine(
  (value) => !value.includes('?') && !value.includes('#'),
  'must not contain a query or fragment',
)

const tagsSchema = z.array(publicSlugSchema).superRefine((tags, context) => {
  if (new Set(tags).size !== tags.length) {
    context.addIssue({ code: 'custom', message: 'must not contain duplicate tags' })
  }
})

const articleSummaryResponseSchema = z.strictObject({
  id: z.string().uuid(),
  slug: publicSlugSchema,
  title: z.string().min(1).max(180),
  summary: z.string().min(1).max(500),
  category: publicSlugSchema.nullable(),
  tags: tagsSchema,
  publishedAt: instantSchema,
  updatedAt: instantSchema,
})

const articleDetailResponseSchema = z.strictObject({
  ...articleSummaryResponseSchema.shape,
  contentHtml: safeHtmlSchema,
  seoTitle: z.string().max(70).nullable(),
  seoDescription: z.string().max(160).nullable(),
  canonicalPath: canonicalPathSchema,
}).superRefine((article, context) => {
  if (article.canonicalPath !== `/blog/${article.slug}`) {
    context.addIssue({ code: 'custom', path: ['canonicalPath'], message: 'must match the article slug' })
  }
})

const taxonomyResponseSchema = z.strictObject({
  name: z.string().min(1).max(120),
  slug: publicSlugSchema,
  publishedArticleCount: z.number().int().min(1),
})

const articlePageResponseSchema = createPageSchema(articleSummaryResponseSchema)
const taxonomyListResponseSchema = z.array(taxonomyResponseSchema)

const articleListParamsSchema = z.strictObject({
  page: pageNumberSchema.default(0),
  size: pageSizeSchema.default(10),
  category: publicSlugSchema.optional(),
  tag: publicSlugSchema.optional(),
})

export interface ArticleSummary {
  id: string
  slug: string
  title: string
  summary: string
  category: string | null
  tags: string[]
  publishedAt: string
  updatedAt: string
}

export interface ArticleDetail extends ArticleSummary {
  contentHtml: SafeHtml
  seoTitle: string | null
  seoDescription: string | null
  canonicalPath: string
}

export interface Taxonomy {
  name: string
  slug: string
  publishedArticleCount: number
}

export interface ArticlePage {
  items: ArticleSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ArticleListParams {
  page?: number
  size?: number
  category?: string
  tag?: string
}

export interface NormalizedArticleListParams {
  page: number
  size: number
  category?: string
  tag?: string
}

function mapArticleSummary(article: z.infer<typeof articleSummaryResponseSchema>): ArticleSummary {
  return {
    id: article.id,
    slug: article.slug,
    title: article.title,
    summary: article.summary,
    category: article.category,
    tags: article.tags.map((tag) => tag),
    publishedAt: article.publishedAt,
    updatedAt: article.updatedAt,
  }
}

export function parseArticlePage(input: unknown): ArticlePage {
  const page = articlePageResponseSchema.parse(input)
  return {
    items: page.items.map(mapArticleSummary),
    page: page.page,
    size: page.size,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
  }
}

export function parseArticleDetail(input: unknown): ArticleDetail {
  const article = articleDetailResponseSchema.parse(input)
  return {
    ...mapArticleSummary(article),
    contentHtml: article.contentHtml,
    seoTitle: article.seoTitle,
    seoDescription: article.seoDescription,
    canonicalPath: article.canonicalPath,
  }
}

export function parseTaxonomies(input: unknown): Taxonomy[] {
  return taxonomyListResponseSchema.parse(input).map((taxonomy) => ({
    name: taxonomy.name,
    slug: taxonomy.slug,
    publishedArticleCount: taxonomy.publishedArticleCount,
  }))
}

export function parseArticleListParams(input: unknown = {}): NormalizedArticleListParams {
  const params = articleListParamsSchema.parse(input)
  return {
    page: params.page,
    size: params.size,
    ...(params.category === undefined ? {} : { category: params.category }),
    ...(params.tag === undefined ? {} : { tag: params.tag }),
  }
}

export function parseArticleSlug(input: unknown): string {
  return publicSlugSchema.parse(input)
}
