import { describe, expect, it } from 'vitest'
import {
  parseAdminArticle,
  parseAdminArticlePage,
  parseAdminExperienceList,
  parseAdminPreview,
  parseAdminProblem,
  parseAdminProfile,
  parseAdminProject,
  parseAdminSkillList,
  parseAdminTaxonomyList,
} from './admin-schema'

const id = '2d65e30a-f450-4f8e-8ed9-5f36b2f7c322'
const instant = '2026-07-20T10:00:00Z'
const article = {
  id, slug: 'article-slug', title: 'Title', summary: 'Summary', bodyMarkdown: '# Body',
  status: 'DRAFT', categoryId: null, tagIds: [], seoTitle: null, seoDescription: null,
  publishedAt: null, createdAt: instant, updatedAt: instant, version: 0,
}

describe('admin runtime schemas', () => {
  it('parses strict article details and pages with exact states', () => {
    expect(parseAdminArticle(article)).toEqual(article)
    expect(parseAdminArticlePage({
      items: [{ id, slug: 'article-slug', title: 'Title', summary: 'Summary', status: 'DRAFT',
        publishedAt: null, updatedAt: instant, version: 0 }],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    }).items).toHaveLength(1)
    expect(() => parseAdminArticle({ ...article, status: 'UNKNOWN' })).toThrow()
    expect(() => parseAdminArticle({ ...article, version: -1 })).toThrow()
    expect(() => parseAdminArticle({ ...article, leaked: true })).toThrow()
  })

  it('parses taxonomy, portfolio and safe preview DTOs', () => {
    expect(parseAdminTaxonomyList([{ id, name: 'Java', slug: 'java', articleCount: 0,
      createdAt: instant, updatedAt: instant, version: 0 }])).toHaveLength(1)
    expect(parseAdminProfile({ id: 1, displayName: 'Name', headline: 'Headline', bioMarkdown: '',
      seoDescription: null, createdAt: instant, updatedAt: instant, version: 0 }).id).toBe(1)
    expect(parseAdminSkillList([{ id, name: 'Java', category: 'Backend', summary: null,
      sortOrder: 0, visible: true, createdAt: instant, updatedAt: instant, version: 0 }])).toHaveLength(1)
    expect(parseAdminExperienceList([{ id, organization: 'Org', role: 'Role',
      startDate: '2020-01-01', endDate: null, summaryMarkdown: '', sortOrder: 0, visible: true,
      createdAt: instant, updatedAt: instant, version: 0 }])).toHaveLength(1)
    expect(parseAdminProject({ id, slug: 'project', title: 'Project', summary: 'Summary',
      descriptionMarkdown: '', projectUrl: 'https://example.com', repositoryUrl: null,
      status: 'DRAFT', featured: false, sortOrder: 0, publishedAt: null,
      createdAt: instant, updatedAt: instant, version: 0 }).projectUrl).toBe('https://example.com')
    expect(parseAdminPreview({ html: '<p>Safe</p>' }).html).toBe('<p>Safe</p>')
    expect(() => parseAdminPreview({ html: '<script>alert(1)</script>' })).toThrow()
  })

  it('parses strict field errors and rejects malformed timestamps and urls', () => {
    expect(parseAdminProblem({ type: 'urn:test', title: 'Invalid', status: 400,
      detail: 'Invalid', instance: '/api/v1/admin/articles', code: 'validation_failed',
      fieldErrors: { title: ['is required'] } }).fieldErrors?.title).toEqual(['is required'])
    expect(() => parseAdminArticle({ ...article, updatedAt: 'yesterday' })).toThrow()
    expect(() => parseAdminProject({ id, slug: 'project', title: 'Project', summary: 'Summary',
      descriptionMarkdown: '', projectUrl: 'http://example.com', repositoryUrl: null,
      status: 'DRAFT', featured: false, sortOrder: 0, publishedAt: null,
      createdAt: instant, updatedAt: instant, version: 0 })).toThrow()
  })
})
