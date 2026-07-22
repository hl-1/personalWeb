import { z } from 'zod'

const blankToNull = (value: unknown) => typeof value === 'string' && value.trim() === '' ? null : value

export const adminSlugSchema = z.string()
  .trim()
  .toLowerCase()
  .min(3, 'Use at least 3 characters')
  .max(120, 'Use at most 120 characters')
  .regex(/^[a-z0-9]+(?:-[a-z0-9]+)*$/, 'Use lowercase letters, numbers, and single hyphens')

export const requiredTextSchema = (maximum: number) => z.string()
  .trim()
  .min(1, 'This field is required')
  .max(maximum, `Use at most ${maximum} characters`)

export const limitedTextSchema = (maximum: number) => z.string()
  .max(maximum, `Use at most ${maximum} characters`)

export const optionalTextSchema = (maximum: number) => z.preprocess(
  blankToNull,
  z.string().trim().max(maximum, `Use at most ${maximum} characters`).nullable(),
)

const httpsUrlSchema = z.string()
  .trim()
  .max(2048, 'Use at most 2048 characters')
  .superRefine((value, context) => {
    try {
      const url = new URL(value)
      if (url.protocol !== 'https:' || !url.hostname || url.username || url.password) {
        context.addIssue({ code: 'custom', message: 'Use an HTTPS URL without credentials' })
      }
    } catch {
      context.addIssue({ code: 'custom', message: 'Use an HTTPS URL without credentials' })
    }
  })

export const optionalHttpsUrlSchema = z.preprocess(blankToNull, httpsUrlSchema.nullable())

export const nonNegativeIntegerSchema = z.number()
  .int('Use a whole number')
  .min(0, 'Use zero or a positive number')

export const nullableVersionSchema = nonNegativeIntegerSchema.nullable()

export const dateSchema = z.string()
  .regex(/^\d{4}-\d{2}-\d{2}$/, 'Use a valid date')

export const optionalDateSchema = z.preprocess(blankToNull, dateSchema.nullable())

export const utcInstantSchema = z.string().regex(
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/,
  'Use a valid UTC date and time',
)
