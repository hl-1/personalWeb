import { describe, expect, it } from 'vitest'
import { z } from 'zod'
import { createAdminFormValidation } from './admin-form-validation'

const schema = z.strictObject({
  slug: z.string().min(3, 'Use at least 3 characters'),
  title: z.string().trim().min(1, 'Title is required'),
  endDate: z.string().nullable(),
}).superRefine((value, context) => {
  if (value.endDate === '2020-01-01') {
    context.addIssue({ code: 'custom', path: ['endDate'], message: 'End date is too early' })
  }
})

const invalid = { slug: 'a', title: '', endDate: null }
const valid = { slug: 'article', title: 'Title', endDate: null }

describe('admin form validation', () => {
  it('validates touched and submitted fields, then clears corrected errors', () => {
    const validation = createAdminFormValidation(schema)

    expect(validation.errorsFor('slug')).toEqual([])
    validation.touch('slug', invalid)

    expect(validation.errorsFor('slug')).toEqual(['Use at least 3 characters'])
    expect(validation.errorsFor('title')).toEqual([])

    validation.change('slug', valid)
    expect(validation.errorsFor('slug')).toEqual([])

    const failed = validation.validateForSubmit(invalid, ['title', 'slug', 'endDate'])
    expect(failed).toEqual({ success: false, firstInvalidField: 'title' })
    expect(validation.errorsFor('title')).toEqual(['Title is required'])

    expect(validation.validateForSubmit(valid, ['title', 'slug', 'endDate']))
      .toEqual({ success: true, data: valid })
  })

  it('merges backend field errors, supports aliases and clears an edited server field', () => {
    const validation = createAdminFormValidation(schema)

    validation.applyServerErrors({ slug: ['Already in use'], dateRangeValid: ['Invalid range'] }, {
      dateRangeValid: 'endDate',
    })

    expect(validation.errorsFor('slug')).toEqual(['Already in use'])
    expect(validation.errorsFor('endDate')).toEqual(['Invalid range'])

    validation.change('slug', valid)
    expect(validation.errorsFor('slug')).toEqual([])
    expect(validation.errorsFor('endDate')).toEqual(['Invalid range'])
  })
})
