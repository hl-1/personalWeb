import { describe, expect, it } from 'vitest'
import { parseTaxonomyCreateForm, parseTaxonomyUpdateForm } from './taxonomy-form-schema'

describe('taxonomy form schema', () => {
  it('parses create and update values', () => {
    expect(parseTaxonomyCreateForm({ name: 'Java', slug: 'java' }))
      .toEqual({ name: 'Java', slug: 'java' })
    expect(parseTaxonomyUpdateForm({ name: 'Java', slug: 'java', version: 0 }).version).toBe(0)
  })

  it.each([
    { name: '', slug: 'java' },
    { name: 'Java', slug: 'Bad Slug' },
    { name: 'Java', slug: 'ab' },
    { name: 'Java', slug: 'java', version: -1 },
    { name: 'Java', slug: 'java', version: 0, unknown: true },
  ])('rejects invalid or historical values', (value) => {
    expect(() => 'version' in value
      ? parseTaxonomyUpdateForm(value)
      : parseTaxonomyCreateForm(value)).toThrow()
  })
})
