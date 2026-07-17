import { z } from 'zod'
import { createPageSchema, pageNumberSchema, pageSizeSchema } from '../../shared/api/page-schema'
import { publicSlugSchema } from '../../shared/api/slug-schema'
import { safeHtmlSchema, type SafeHtml } from '../../shared/content/safe-html'

const instantSchema = z.string().regex(
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/,
  'must be a UTC ISO-8601 instant',
)
const localDateSchema = z.string().refine((value) => {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false
  }
  const date = new Date(`${value}T00:00:00Z`)
  return !Number.isNaN(date.valueOf()) && date.toISOString().startsWith(value)
}, 'must be a valid ISO-8601 date')
const httpsUrlSchema = z.string().max(2048).superRefine((value, context) => {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password) {
      context.addIssue({ code: 'custom', message: 'must be an absolute HTTPS URL without credentials' })
    }
  } catch {
    context.addIssue({ code: 'custom', message: 'must be an absolute HTTPS URL without credentials' })
  }
})

const projectSummaryResponseSchema = z.strictObject({
  id: z.string().uuid(),
  slug: publicSlugSchema,
  title: z.string().min(1).max(180),
  summary: z.string().min(1).max(500),
  featured: z.boolean(),
  publishedAt: instantSchema,
  updatedAt: instantSchema,
})

const projectDetailResponseSchema = z.strictObject({
  ...projectSummaryResponseSchema.shape,
  descriptionHtml: safeHtmlSchema,
  projectUrl: httpsUrlSchema.nullable(),
  repositoryUrl: httpsUrlSchema.nullable(),
  canonicalPath: z.string().startsWith('/'),
}).superRefine((project, context) => {
  if (project.canonicalPath !== `/projects/${project.slug}`) {
    context.addIssue({ code: 'custom', path: ['canonicalPath'], message: 'must match the project slug' })
  }
})

const profileResponseSchema = z.strictObject({
  displayName: z.string().min(1).max(120),
  headline: z.string().min(1).max(180),
  bioHtml: safeHtmlSchema,
  seoDescription: z.string().max(160).nullable(),
})

const skillResponseSchema = z.strictObject({
  id: z.string().uuid(),
  name: z.string().min(1).max(120),
  category: z.string().min(1).max(120),
  summary: z.string().max(500).nullable(),
})

const experienceResponseSchema = z.strictObject({
  id: z.string().uuid(),
  organization: z.string().min(1).max(180),
  role: z.string().min(1).max(180),
  startDate: localDateSchema,
  endDate: localDateSchema.nullable(),
  summaryHtml: safeHtmlSchema,
}).superRefine((experience, context) => {
  if (experience.endDate !== null && experience.endDate < experience.startDate) {
    context.addIssue({ code: 'custom', path: ['endDate'], message: 'must not be before startDate' })
  }
})

const projectPageResponseSchema = createPageSchema(projectSummaryResponseSchema)
const skillsResponseSchema = z.array(skillResponseSchema)
const experiencesResponseSchema = z.array(experienceResponseSchema)
const projectListParamsSchema = z.strictObject({
  page: pageNumberSchema.default(0),
  size: pageSizeSchema.default(10),
  featured: z.boolean().optional(),
})

export interface PortfolioProfile {
  displayName: string
  headline: string
  bioHtml: SafeHtml
  seoDescription: string | null
}

export interface ProjectSummary {
  id: string
  slug: string
  title: string
  summary: string
  featured: boolean
  publishedAt: string
  updatedAt: string
}

export interface ProjectDetail extends ProjectSummary {
  descriptionHtml: SafeHtml
  projectUrl: string | null
  repositoryUrl: string | null
  canonicalPath: string
}

export interface ProjectPage {
  items: ProjectSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Skill {
  id: string
  name: string
  category: string
  summary: string | null
}

export interface Experience {
  id: string
  organization: string
  role: string
  startDate: string
  endDate: string | null
  summaryHtml: SafeHtml
}

export interface ProjectListParams {
  page?: number
  size?: number
  featured?: boolean
}

export interface NormalizedProjectListParams {
  page: number
  size: number
  featured?: boolean
}

function mapProjectSummary(project: z.infer<typeof projectSummaryResponseSchema>): ProjectSummary {
  return {
    id: project.id,
    slug: project.slug,
    title: project.title,
    summary: project.summary,
    featured: project.featured,
    publishedAt: project.publishedAt,
    updatedAt: project.updatedAt,
  }
}

export function parsePortfolioProfile(input: unknown): PortfolioProfile {
  const profile = profileResponseSchema.parse(input)
  return {
    displayName: profile.displayName,
    headline: profile.headline,
    bioHtml: profile.bioHtml,
    seoDescription: profile.seoDescription,
  }
}

export function parseProjectPage(input: unknown): ProjectPage {
  const page = projectPageResponseSchema.parse(input)
  return {
    items: page.items.map(mapProjectSummary),
    page: page.page,
    size: page.size,
    totalElements: page.totalElements,
    totalPages: page.totalPages,
  }
}

export function parseProjectDetail(input: unknown): ProjectDetail {
  const project = projectDetailResponseSchema.parse(input)
  return {
    ...mapProjectSummary(project),
    descriptionHtml: project.descriptionHtml,
    projectUrl: project.projectUrl,
    repositoryUrl: project.repositoryUrl,
    canonicalPath: project.canonicalPath,
  }
}

export function parseSkills(input: unknown): Skill[] {
  return skillsResponseSchema.parse(input).map((skill) => ({
    id: skill.id,
    name: skill.name,
    category: skill.category,
    summary: skill.summary,
  }))
}

export function parseExperiences(input: unknown): Experience[] {
  return experiencesResponseSchema.parse(input).map((experience) => ({
    id: experience.id,
    organization: experience.organization,
    role: experience.role,
    startDate: experience.startDate,
    endDate: experience.endDate,
    summaryHtml: experience.summaryHtml,
  }))
}

export function parseProjectListParams(input: unknown = {}): NormalizedProjectListParams {
  const params = projectListParamsSchema.parse(input)
  return {
    page: params.page,
    size: params.size,
    ...(params.featured === undefined ? {} : { featured: params.featured }),
  }
}

export function parseProjectSlug(input: unknown): string {
  return publicSlugSchema.parse(input)
}
