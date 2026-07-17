import { z } from 'zod'

export const pageNumberSchema = z.number().int().min(0)
export const pageSizeSchema = z.number().int().min(1).max(50)

export function createPageSchema<ItemSchema extends z.ZodType>(itemSchema: ItemSchema) {
  return z.strictObject({
    items: z.array(itemSchema),
    page: pageNumberSchema,
    size: pageSizeSchema,
    totalElements: z.number().int().min(0),
    totalPages: z.number().int().min(0),
  })
}
