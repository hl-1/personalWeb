import { z } from 'zod'

export const publicSlugSchema = z.string()
  .min(3)
  .max(120)
  .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/)

export function parseOptionalPublicSlug(input: unknown): string | undefined {
  const result = publicSlugSchema.safeParse(input)
  return result.success ? result.data : undefined
}
