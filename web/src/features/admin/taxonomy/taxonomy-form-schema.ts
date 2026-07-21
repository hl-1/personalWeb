import { z } from 'zod'

const createSchema = z.strictObject({
  name: z.string().trim().min(1).max(120),
  slug: z.string().min(3).max(120).regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/),
})
const updateSchema = createSchema.extend({ version: z.number().int().min(0) })

export type TaxonomyCreateForm = z.infer<typeof createSchema>
export type TaxonomyUpdateForm = z.infer<typeof updateSchema>

export function parseTaxonomyCreateForm(input: unknown): TaxonomyCreateForm {
  return createSchema.parse(input)
}

export function parseTaxonomyUpdateForm(input: unknown): TaxonomyUpdateForm {
  return updateSchema.parse(input)
}
