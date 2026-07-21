import { z } from 'zod'
import { publicSlugSchema } from '../../shared/api/slug-schema'
import { safeHtmlSchema, type SafeHtml } from '../../shared/content/safe-html'
import { parseApiProblem, type ApiProblem } from '../../shared/api/problem-schema'

const uuidSchema = z.string().uuid()
const nonNegativeInteger = z.number().int().min(0)
const instantSchema = z.string().regex(
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/,
  'must be a UTC ISO-8601 instant',
)
const dateSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}$/)
const statusSchema = z.enum(['DRAFT', 'PUBLISHED', 'ARCHIVED'])
const httpsUrlSchema = z.string().max(2048).superRefine((value, context) => {
  try {
    const parsed = new URL(value)
    if (parsed.protocol !== 'https:' || !parsed.hostname || parsed.username || parsed.password) {
      context.addIssue({ code: 'custom', message: 'must be an HTTPS URL without credentials' })
    }
  } catch {
    context.addIssue({ code: 'custom', message: 'must be an HTTPS URL without credentials' })
  }
})

const articleSummarySchema = z.strictObject({
  id: uuidSchema,
  slug: publicSlugSchema,
  title: z.string().min(1).max(180),
  summary: z.string().min(1).max(500),
  status: statusSchema,
  publishedAt: instantSchema.nullable(),
  updatedAt: instantSchema,
  version: nonNegativeInteger,
})

const articleSchema = z.strictObject({
  ...articleSummarySchema.shape,
  bodyMarkdown: z.string().max(200_000),
  categoryId: uuidSchema.nullable(),
  tagIds: z.array(uuidSchema).max(10).superRefine((values, context) => {
    if (new Set(values).size !== values.length) {
      context.addIssue({ code: 'custom', message: 'must not contain duplicate tags' })
    }
  }),
  seoTitle: z.string().max(70).nullable(),
  seoDescription: z.string().max(160).nullable(),
  createdAt: instantSchema,
})

const articlePageSchema = z.strictObject({
  items: z.array(articleSummarySchema),
  page: nonNegativeInteger,
  size: z.number().int().min(1).max(100),
  totalElements: nonNegativeInteger,
  totalPages: nonNegativeInteger,
})

const taxonomySchema = z.strictObject({
  id: uuidSchema,
  name: z.string().min(1).max(120),
  slug: publicSlugSchema,
  articleCount: nonNegativeInteger,
  createdAt: instantSchema,
  updatedAt: instantSchema,
  version: nonNegativeInteger,
})

const projectSchema = z.strictObject({
  id: uuidSchema,
  slug: publicSlugSchema,
  title: z.string().min(1).max(180),
  summary: z.string().min(1).max(500),
  descriptionMarkdown: z.string().max(100_000),
  projectUrl: httpsUrlSchema.nullable(),
  repositoryUrl: httpsUrlSchema.nullable(),
  status: statusSchema,
  featured: z.boolean(),
  sortOrder: nonNegativeInteger,
  publishedAt: instantSchema.nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
  version: nonNegativeInteger,
})

const projectSummarySchema = projectSchema.pick({
  id: true,
  slug: true,
  title: true,
  summary: true,
  status: true,
  featured: true,
  sortOrder: true,
  publishedAt: true,
  updatedAt: true,
  version: true,
})

const projectPageSchema = z.strictObject({
  items: z.array(projectSummarySchema),
  page: nonNegativeInteger,
  size: z.number().int().min(1).max(100),
  totalElements: nonNegativeInteger,
  totalPages: nonNegativeInteger,
})

const profileSchema = z.strictObject({
  id: z.literal(1),
  displayName: z.string().min(1).max(120),
  headline: z.string().min(1).max(180),
  bioMarkdown: z.string().max(50_000),
  seoDescription: z.string().max(160).nullable(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
  version: nonNegativeInteger,
})

const skillSchema = z.strictObject({
  id: uuidSchema,
  name: z.string().min(1).max(120),
  category: z.string().min(1).max(120),
  summary: z.string().max(500).nullable(),
  sortOrder: nonNegativeInteger,
  visible: z.boolean(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
  version: nonNegativeInteger,
})

const experienceSchema = z.strictObject({
  id: uuidSchema,
  organization: z.string().min(1).max(180),
  role: z.string().min(1).max(180),
  startDate: dateSchema,
  endDate: dateSchema.nullable(),
  summaryMarkdown: z.string().max(20_000),
  sortOrder: nonNegativeInteger,
  visible: z.boolean(),
  createdAt: instantSchema,
  updatedAt: instantSchema,
  version: nonNegativeInteger,
}).superRefine((value, context) => {
  if (value.endDate !== null && value.endDate < value.startDate) {
    context.addIssue({ code: 'custom', path: ['endDate'], message: 'must not precede startDate' })
  }
})

const previewSchema = z.strictObject({ html: safeHtmlSchema })
const articleListParamsSchema = z.strictObject({
  page: nonNegativeInteger.default(0),
  size: z.number().int().min(1).max(100).default(20),
  status: statusSchema.optional(),
  query: z.string().max(100).transform((value) => value.trim()).optional(),
})

export type AdminStatus = z.infer<typeof statusSchema>
export type AdminArticle = z.infer<typeof articleSchema>
export type AdminArticleSummary = z.infer<typeof articleSummarySchema>
export type AdminArticlePage = z.infer<typeof articlePageSchema>
export type AdminTaxonomy = z.infer<typeof taxonomySchema>
export type AdminProject = z.infer<typeof projectSchema>
export type AdminProjectPage = z.infer<typeof projectPageSchema>
export type AdminProfile = z.infer<typeof profileSchema>
export type AdminSkill = z.infer<typeof skillSchema>
export type AdminExperience = z.infer<typeof experienceSchema>
export interface AdminPreview { html: SafeHtml }
export interface AdminArticleListParams {
  page?: number
  size?: number
  status?: AdminStatus
  query?: string
}
export interface NormalizedAdminArticleListParams {
  page: number
  size: number
  status?: AdminStatus
  query?: string
}

export function parseAdminArticle(input: unknown): AdminArticle {
  return articleSchema.parse(input)
}

export function parseAdminArticlePage(input: unknown): AdminArticlePage {
  const page = articlePageSchema.parse(input)
  return { ...page, items: page.items.map((item) => ({ ...item })) }
}

export function parseAdminTaxonomyList(input: unknown): AdminTaxonomy[] {
  return z.array(taxonomySchema).parse(input).map((item) => ({ ...item }))
}

export function parseAdminProject(input: unknown): AdminProject {
  return projectSchema.parse(input)
}

export function parseAdminProjectPage(input: unknown): AdminProjectPage {
  const page = projectPageSchema.parse(input)
  return { ...page, items: page.items.map((item) => ({ ...item })) }
}

export function parseAdminProfile(input: unknown): AdminProfile {
  return profileSchema.parse(input)
}

export function parseAdminSkillList(input: unknown): AdminSkill[] {
  return z.array(skillSchema).parse(input).map((item) => ({ ...item }))
}

export function parseAdminSkill(input: unknown): AdminSkill {
  return skillSchema.parse(input)
}

export function parseAdminExperienceList(input: unknown): AdminExperience[] {
  return z.array(experienceSchema).parse(input).map((item) => ({ ...item }))
}

export function parseAdminExperience(input: unknown): AdminExperience {
  return experienceSchema.parse(input)
}

export function parseAdminPreview(input: unknown): AdminPreview {
  const parsed = previewSchema.parse(input)
  return { html: parsed.html }
}

export function parseAdminProblem(input: unknown): ApiProblem {
  return parseApiProblem(input)
}

export function parseAdminArticleListParams(
  input: AdminArticleListParams = {},
): NormalizedAdminArticleListParams {
  const parsed = articleListParamsSchema.parse(input)
  return {
    page: parsed.page,
    size: parsed.size,
    ...(parsed.status === undefined ? {} : { status: parsed.status }),
    ...(parsed.query === undefined || parsed.query === '' ? {} : { query: parsed.query }),
  }
}

export function parseAdminId(input: unknown): string {
  return uuidSchema.parse(input)
}

export function parseAdminVersion(input: unknown): number {
  return nonNegativeInteger.parse(input)
}
