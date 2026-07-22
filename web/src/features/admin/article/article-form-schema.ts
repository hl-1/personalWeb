import { z } from 'zod'
import {
  adminSlugSchema,
  limitedTextSchema,
  nullableVersionSchema,
  optionalTextSchema,
  requiredTextSchema,
  utcInstantSchema,
} from '../admin-form-rules'

const tagIdsSchema = z.array(z.string().uuid())
  .max(10, 'Select at most 10 tags')
  .superRefine((values, context) => {
  if (new Set(values).size !== values.length) {
    context.addIssue({ code: 'custom', message: 'Do not select duplicate tags' })
  }
})

export const articleFormSchema = z.strictObject({
  slug: adminSlugSchema,
  title: requiredTextSchema(180),
  summary: requiredTextSchema(500),
  bodyMarkdown: limitedTextSchema(200_000),
  categoryId: z.string().uuid().nullable(),
  tagIds: tagIdsSchema,
  seoTitle: optionalTextSchema(70),
  seoDescription: optionalTextSchema(160),
  version: nullableVersionSchema,
  publishMode: z.enum(['draft', 'now', 'scheduled']),
  publishAt: utcInstantSchema.nullable(),
}).superRefine((value, context) => {
  if (value.publishMode === 'scheduled' && value.publishAt === null) {
    context.addIssue({ code: 'custom', path: ['publishAt'], message: 'Choose a publication date and time' })
  }
})

export type ArticleForm = z.infer<typeof articleFormSchema>

export function parseArticleForm(input: unknown): ArticleForm {
  const parsed = articleFormSchema.parse(input)
  return { ...parsed, tagIds: [...parsed.tagIds] }
}
