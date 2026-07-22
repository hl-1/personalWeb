import { z } from 'zod'
import {
  adminSlugSchema,
  limitedTextSchema,
  nonNegativeIntegerSchema,
  nullableVersionSchema,
  optionalHttpsUrlSchema,
  requiredTextSchema,
  utcInstantSchema,
} from '../admin-form-rules'

export const projectFormSchema = z.strictObject({
  slug: adminSlugSchema,
  title: requiredTextSchema(180),
  summary: requiredTextSchema(500),
  descriptionMarkdown: limitedTextSchema(100_000),
  projectUrl: optionalHttpsUrlSchema,
  repositoryUrl: optionalHttpsUrlSchema,
  featured: z.boolean(),
  sortOrder: nonNegativeIntegerSchema,
  version: nullableVersionSchema,
  publishMode: z.enum(['draft', 'now', 'scheduled']),
  publishAt: utcInstantSchema.nullable(),
}).superRefine((value, context) => {
  if (value.publishMode === 'scheduled' && value.publishAt === null) {
    context.addIssue({ code: 'custom', path: ['publishAt'], message: 'Choose a publication date and time' })
  }
})

export type ProjectForm = z.infer<typeof projectFormSchema>
export function parseProjectForm(input: unknown): ProjectForm { return projectFormSchema.parse(input) }
