import { describe, expect, it } from 'vitest'
import {
  parseArticleDetail,
  parseArticlePage,
  parseTaxonomies,
} from './content-schema'

const summary = {
  id: '00000000-0000-4000-8000-000000000001',
  slug: 'public-article',
  title: 'Public article',
  summary: 'Article summary',
  category: 'java',
  tags: ['spring'],
  publishedAt: '2026-07-17T06:00:00Z',
  updatedAt: '2026-07-17T07:00:00Z',
}

const page = {
  items: [summary],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
}

describe('content runtime schemas', () => {
  it('parses a detached strict article page', () => {
    const input = structuredClone(page)
    const parsed = parseArticlePage(input)
    input.items[0]!.title = 'changed'

    expect(parsed).toEqual(page)
    expect(parsed).not.toBe(input)
    expect(parsed.items).not.toBe(input.items)
  })

  it('parses article detail HTML and nullable SEO fields', () => {
    expect(parseArticleDetail({
      ...summary,
      contentHtml: '<h1>Safe</h1>',
      seoTitle: null,
      seoDescription: 'Description',
      canonicalPath: '/blog/public-article',
    })).toMatchObject({
      slug: 'public-article',
      contentHtml: '<h1>Safe</h1>',
      canonicalPath: '/blog/public-article',
    })
  })

  it('parses strict taxonomy responses', () => {
    expect(parseTaxonomies([
      { name: 'Java', slug: 'java', publishedArticleCount: 2 },
    ])).toEqual([
      { name: 'Java', slug: 'java', publishedArticleCount: 2 },
    ])
  })

  it.each([
    ['unknown entity status', { ...page, items: [{ ...summary, status: 'PUBLISHED' }] }],
    ['raw markdown', { ...page, items: [{ ...summary, bodyMarkdown: '# secret' }] }],
    ['invalid slug', { ...page, items: [{ ...summary, slug: 'Invalid Slug' }] }],
    ['invalid ISO instant', { ...page, items: [{ ...summary, publishedAt: '2026-07-17' }] }],
    ['negative page', { ...page, page: -1 }],
    ['zero size', { ...page, size: 0 }],
    ['oversized page', { ...page, size: 51 }],
    ['unknown page field', { ...page, sort: 'title' }],
  ])('rejects %s', (_case, input) => {
    expect(() => parseArticlePage(input)).toThrowError()
  })

  it('rejects dangerous detail HTML and unknown fields', () => {
    expect(() => parseArticleDetail({
      ...summary,
      contentHtml: '<p onclick="steal()">Unsafe</p>',
      seoTitle: null,
      seoDescription: null,
      canonicalPath: '/blog/public-article',
      version: 1,
    })).toThrowError()
  })
})
