import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useProjectDraftStore } from './project-draft-store'

const fields = { slug: 'project', title: 'Project', summary: 'Summary', descriptionMarkdown: '',
  projectUrl: null, repositoryUrl: null, featured: false, sortOrder: 0 }

describe('project draft store', () => {
  beforeEach(() => setActivePinia(createPinia()))
  it('isolates resource drafts by version', () => {
    const store = useProjectDraftStore()
    store.save('one', 1, fields)
    expect(store.load('one', 1)).toEqual(fields)
    expect(store.load('one', 2)).toBeUndefined()
    expect(store.load('two', 1)).toBeUndefined()
  })
  it('clears new project drafts', () => {
    const store = useProjectDraftStore()
    store.save(null, null, fields)
    store.clear(null)
    expect(store.load(null, null)).toBeUndefined()
  })
})
