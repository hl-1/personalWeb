import { z } from 'zod'

const fieldErrorsSchema = z.record(z.string(), z.array(z.string().min(1)).min(1))

export const problemDetailSchema = z.strictObject({
  type: z.string().min(1),
  title: z.string().min(1),
  status: z.number().int().min(400).max(599),
  detail: z.string(),
  instance: z.string().startsWith('/'),
  code: z.string().min(1),
  fieldErrors: fieldErrorsSchema.optional(),
})

export interface ApiProblem {
  type: string
  title: string
  status: number
  detail: string
  instance: string
  code: string
  fieldErrors?: Record<string, string[]>
}

export function parseApiProblem(input: unknown): ApiProblem {
  const parsed = problemDetailSchema.parse(input)
  return {
    type: parsed.type,
    title: parsed.title,
    status: parsed.status,
    detail: parsed.detail,
    instance: parsed.instance,
    code: parsed.code,
    ...(parsed.fieldErrors === undefined ? {} : {
      fieldErrors: Object.fromEntries(
        Object.entries(parsed.fieldErrors).map(([field, messages]) => [field, [...messages]]),
      ),
    }),
  }
}
