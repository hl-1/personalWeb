import { describe, expect, it } from 'vitest'
import { parseProjectForm } from './project-form-schema'

const valid = { slug: 'project-name', title: 'Project', summary: 'Summary',
  descriptionMarkdown: '', projectUrl: 'https://example.com/demo', repositoryUrl: null,
  featured: false, sortOrder: 0, version: 0, publishMode: 'draft', publishAt: null }

describe('project form schema', () => {
  it('parses valid project fields', () => expect(parseProjectForm(valid)).toEqual(valid))

  it('normalizes the slug and blank optional URLs', () => {
    expect(parseProjectForm({
      ...valid, slug: '  PROJECT-NAME  ', projectUrl: '', repositoryUrl: '',
    })).toMatchObject({ slug: 'project-name', projectUrl: null, repositoryUrl: null })
  })

  it.each([
    { ...valid, slug: 'Bad Slug' }, { ...valid, title: '' },
    { ...valid, summary: 'x'.repeat(501) }, { ...valid, descriptionMarkdown: 'x'.repeat(100_001) },
    { ...valid, projectUrl: 'http://example.com' }, { ...valid, repositoryUrl: 'https://user@example.com/repo' },
    { ...valid, sortOrder: -1 }, { ...valid, featured: 'yes' },
    { ...valid, version: -1 }, { ...valid, publishMode: 'later' },
  ])('rejects invalid fields', (input) => expect(() => parseProjectForm(input)).toThrow())
})
