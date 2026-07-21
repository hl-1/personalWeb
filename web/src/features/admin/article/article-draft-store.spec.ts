import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useArticleDraftStore } from './article-draft-store'

const fields = {
  slug: 'draft', title: 'Draft', summary: 'Summary', bodyMarkdown: '', categoryId: null,
  tagIds: [], seoTitle: null, seoDescription: null,
}

describe('article draft store', () => {
  beforeEach(() => { setActivePinia(createPinia()) })

  it('isolates drafts by resource and server version', () => {
    const store = useArticleDraftStore()
    store.save('article-1', 2, fields)
    expect(store.load('article-1', 2)).toEqual(fields)
    expect(store.load('article-1', 3)).toBeUndefined()
    expect(store.load('article-2', 2)).toBeUndefined()
  })

  it('isolates new drafts and clears saved or discarded entries', () => {
    const store = useArticleDraftStore()
    store.save(null, null, fields)
    expect(store.load(null, null)?.title).toBe('Draft')
    store.clear(null)
    expect(store.load(null, null)).toBeUndefined()
  })
})
