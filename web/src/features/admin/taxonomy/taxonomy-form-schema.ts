import { z } from 'zod'
import { adminSlugSchema, nonNegativeIntegerSchema, requiredTextSchema } from '../admin-form-rules'

export const taxonomyCreateSchema = z.strictObject({
  name: requiredTextSchema(120),
  slug: adminSlugSchema,
})
export const taxonomyUpdateSchema = taxonomyCreateSchema.extend({ version: nonNegativeIntegerSchema })

export type TaxonomyCreateForm = z.infer<typeof taxonomyCreateSchema>
export type TaxonomyUpdateForm = z.infer<typeof taxonomyUpdateSchema>

export function parseTaxonomyCreateForm(input: unknown): TaxonomyCreateForm {
  return taxonomyCreateSchema.parse(input)
}

export function parseTaxonomyUpdateForm(input: unknown): TaxonomyUpdateForm {
  return taxonomyUpdateSchema.parse(input)
}
