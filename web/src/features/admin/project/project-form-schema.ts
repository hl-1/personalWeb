import { z } from 'zod'

const httpsUrl = z.string().max(2048).superRefine((value, context) => {
  try {
    const url = new URL(value)
    if (url.protocol !== 'https:' || !url.hostname || url.username || url.password) {
      context.addIssue({ code: 'custom', message: 'must be an HTTPS URL without credentials' })
    }
  } catch {
    context.addIssue({ code: 'custom', message: 'must be an HTTPS URL without credentials' })
  }
})
const instant = z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/)
const schema = z.strictObject({
  slug: z.string().min(3).max(120).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
  title: z.string().trim().min(1).max(180),
  summary: z.string().trim().min(1).max(500),
  descriptionMarkdown: z.string().max(100_000),
  projectUrl: httpsUrl.nullable(),
  repositoryUrl: httpsUrl.nullable(),
  featured: z.boolean(),
  sortOrder: z.number().int().min(0),
  version: z.number().int().min(0).nullable(),
  publishMode: z.enum(['draft', 'now', 'scheduled']),
  publishAt: instant.nullable(),
}).superRefine((value, context) => {
  if (value.publishMode === 'scheduled' && value.publishAt === null) {
    context.addIssue({ code: 'custom', path: ['publishAt'], message: 'is required' })
  }
})

export type ProjectForm = z.infer<typeof schema>
export function parseProjectForm(input: unknown): ProjectForm { return schema.parse(input) }
