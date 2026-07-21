import { z } from 'zod'

const instantSchema = z.string().regex(
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/,
  'must be a UTC ISO-8601 instant',
)
const tagIdsSchema = z.array(z.string().uuid()).max(10).superRefine((values, context) => {
  if (new Set(values).size !== values.length) {
    context.addIssue({ code: 'custom', message: 'must not contain duplicate tags' })
  }
})

const articleFormSchema = z.strictObject({
  slug: z.string().min(1).max(120).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
  title: z.string().trim().min(1).max(180),
  summary: z.string().trim().min(1).max(500),
  bodyMarkdown: z.string().max(200_000),
  categoryId: z.string().uuid().nullable(),
  tagIds: tagIdsSchema,
  seoTitle: z.string().max(70).nullable(),
  seoDescription: z.string().max(160).nullable(),
  version: z.number().int().min(0).nullable(),
  publishMode: z.enum(['draft', 'now', 'scheduled']),
  publishAt: instantSchema.nullable(),
}).superRefine((value, context) => {
  if (value.publishMode === 'scheduled' && value.publishAt === null) {
    context.addIssue({ code: 'custom', path: ['publishAt'], message: 'is required' })
  }
})

export type ArticleForm = z.infer<typeof articleFormSchema>

export function parseArticleForm(input: unknown): ArticleForm {
  const parsed = articleFormSchema.parse(input)
  return { ...parsed, tagIds: [...parsed.tagIds] }
}
