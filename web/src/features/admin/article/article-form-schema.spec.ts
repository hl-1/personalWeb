import { describe, expect, it } from 'vitest'
import { parseArticleForm } from './article-form-schema'

const id = '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'
const valid = {
  slug: 'valid-slug', title: 'Title', summary: 'Summary', bodyMarkdown: '',
  categoryId: null, tagIds: [id], seoTitle: null, seoDescription: null,
  version: 0, publishMode: 'draft', publishAt: null,
}

describe('article form schema', () => {
  it('accepts a complete valid form', () => {
    expect(parseArticleForm(valid)).toEqual(valid)
  })

  it.each([
    ['slug', { ...valid, slug: 'Bad Slug' }],
    ['title', { ...valid, title: ' ' }],
    ['summary', { ...valid, summary: 'x'.repeat(501) }],
    ['bodyMarkdown', { ...valid, bodyMarkdown: 'x'.repeat(200_001) }],
    ['seoTitle', { ...valid, seoTitle: 'x'.repeat(71) }],
    ['version', { ...valid, version: -1 }],
    ['publishMode', { ...valid, publishMode: 'later' }],
  ])('rejects invalid %s values', (_field, input) => {
    expect(() => parseArticleForm(input)).toThrow()
  })

  it('rejects more than ten tags and duplicates', () => {
    expect(() => parseArticleForm({ ...valid, tagIds: Array.from({ length: 11 }, () => id) })).toThrow()
    expect(() => parseArticleForm({ ...valid, tagIds: [id, id] })).toThrow()
  })

  it('requires a UTC instant only for scheduled publication', () => {
    expect(() => parseArticleForm({ ...valid, publishMode: 'scheduled', publishAt: null })).toThrow()
    expect(parseArticleForm({ ...valid, publishMode: 'scheduled', publishAt: '2026-08-01T10:00:00Z' }).publishAt)
      .toBe('2026-08-01T10:00:00Z')
  })
})
