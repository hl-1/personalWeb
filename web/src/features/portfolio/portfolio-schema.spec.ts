import { describe, expect, it } from 'vitest'
import {
  parseExperiences,
  parsePortfolioProfile,
  parseProjectDetail,
  parseProjectPage,
  parseSkills,
} from './portfolio-schema'

const project = {
  id: '00000000-0000-4000-8000-000000000001',
  slug: 'public-project',
  title: 'Public project',
  summary: 'Project summary',
  featured: true,
  publishedAt: '2026-07-17T06:00:00Z',
  updatedAt: '2026-07-17T07:00:00Z',
}

const page = {
  items: [project],
  page: 0,
  size: 10,
  totalElements: 1,
  totalPages: 1,
}

describe('portfolio runtime schemas', () => {
  it('parses strict profile and project responses', () => {
    expect(parsePortfolioProfile({
      displayName: 'StudyStack Author',
      headline: 'Platform engineer',
      bioHtml: '<p>Safe biography</p>',
      seoDescription: null,
    })).toMatchObject({ displayName: 'StudyStack Author' })
    expect(parseProjectPage(page)).toEqual(page)
    expect(parseProjectDetail({
      ...project,
      descriptionHtml: '<p>Safe project</p>',
      projectUrl: 'https://example.com/project',
      repositoryUrl: null,
      canonicalPath: '/projects/public-project',
    })).toMatchObject({ slug: 'public-project' })
  })

  it('parses visible skill and experience DTOs without internal ordering fields', () => {
    expect(parseSkills([{
      id: '00000000-0000-4000-8000-000000000002',
      name: 'Java',
      category: 'Backend',
      summary: null,
    }])).toHaveLength(1)
    expect(parseExperiences([{
      id: '00000000-0000-4000-8000-000000000003',
      organization: 'StudyStack',
      role: 'Engineer',
      startDate: '2024-01-01',
      endDate: null,
      summaryHtml: '<strong>Built safely</strong>',
    }])).toHaveLength(1)
  })

  it.each([
    ['entity status', { ...page, items: [{ ...project, status: 'PUBLISHED' }] }],
    ['internal sort order', { ...page, items: [{ ...project, sortOrder: 1 }] }],
    ['invalid slug', { ...page, items: [{ ...project, slug: 'Invalid Project' }] }],
    ['invalid timestamp', { ...page, items: [{ ...project, updatedAt: 'yesterday' }] }],
    ['invalid page size', { ...page, size: 51 }],
  ])('rejects %s', (_case, input) => {
    expect(() => parseProjectPage(input)).toThrowError()
  })

  it('rejects unsafe URLs, dangerous HTML, invalid dates, and unknown fields', () => {
    expect(() => parseProjectDetail({
      ...project,
      descriptionHtml: '<iframe src="https://example.com"></iframe>',
      projectUrl: 'http://example.com/project',
      repositoryUrl: null,
      canonicalPath: '/projects/public-project',
    })).toThrowError()
    expect(() => parseExperiences([{
      id: '00000000-0000-4000-8000-000000000003',
      organization: 'StudyStack',
      role: 'Engineer',
      startDate: '2024-02-30',
      endDate: null,
      summaryHtml: '<p>Safe</p>',
      summaryMarkdown: 'secret',
    }])).toThrowError()
  })
})
