import { z } from 'zod'

const sameOriginBase = new URL('https://studystack.invalid')

const apiBasePathSchema = z.string().superRefine((value, context) => {
  if (!value.startsWith('/') || value.startsWith('//')) {
    context.addIssue({
      code: 'custom',
      message: 'must be a same-origin absolute path starting with one slash',
    })
    return
  }

  const parsed = new URL(value, sameOriginBase)
  if (parsed.origin !== sameOriginBase.origin || parsed.username || parsed.password) {
    context.addIssue({
      code: 'custom',
      message: 'must not contain a protocol, host, or credentials',
    })
  }
  if (parsed.search) {
    context.addIssue({
      code: 'custom',
      message: 'must not contain a query string',
    })
  }
  if (parsed.hash) {
    context.addIssue({
      code: 'custom',
      message: 'must not contain a fragment',
    })
  }
})

const publicEnvSchema = z.object({
  VITE_API_BASE_URL: apiBasePathSchema,
})

export type PublicEnv = z.infer<typeof publicEnvSchema>

export function parsePublicEnv(input: Record<string, unknown>): PublicEnv {
  const result = publicEnvSchema.safeParse(input)
  if (result.success) {
    return result.data
  }

  const details = result.error.issues
    .map((issue) => `${issue.path.join('.') || 'public environment'}: ${issue.message}`)
    .join('; ')
  throw new Error(`Invalid public environment: ${details}`)
}

export function loadPublicEnv(input: Record<string, unknown> = import.meta.env): PublicEnv {
  return parsePublicEnv(input)
}
