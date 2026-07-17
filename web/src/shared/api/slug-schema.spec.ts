import { describe, expect, it } from 'vitest'
import { parseOptionalPublicSlug, publicSlugSchema } from './slug-schema'

describe('publicSlugSchema', () => {
  it('accepts the shared public slug contract', () => {
    expect(publicSlugSchema.parse('java-21')).toBe('java-21')
  })

  it('rejects values outside the length and format contract', () => {
    for (const value of ['ab', 'A-B-C', '-java', 'java--spring', 'java spring', '中-java', 'a'.repeat(121)]) {
      expect(() => publicSlugSchema.parse(value)).toThrow()
    }
  })
})

describe('parseOptionalPublicSlug', () => {
  it('returns only valid string query values', () => {
    expect(parseOptionalPublicSlug('spring')).toBe('spring')
    expect(parseOptionalPublicSlug('ab')).toBeUndefined()
    expect(parseOptionalPublicSlug(['spring'])).toBeUndefined()
    expect(parseOptionalPublicSlug(undefined)).toBeUndefined()
  })
})
