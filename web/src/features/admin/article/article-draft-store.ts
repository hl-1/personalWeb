import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ArticleDraftFields {
  slug: string
  title: string
  summary: string
  bodyMarkdown: string
  categoryId: string | null
  tagIds: string[]
  seoTitle: string | null
  seoDescription: string | null
}

interface StoredArticleDraft {
  serverVersion: number | null
  fields: ArticleDraftFields
}

function key(resourceId: string | null): string {
  return resourceId === null ? 'new' : `resource:${resourceId}`
}

function copy(fields: ArticleDraftFields): ArticleDraftFields {
  return { ...fields, tagIds: [...fields.tagIds] }
}

export const useArticleDraftStore = defineStore('admin-article-drafts', () => {
  const drafts = ref<Record<string, StoredArticleDraft>>({})

  function save(resourceId: string | null, serverVersion: number | null, fields: ArticleDraftFields) {
    drafts.value[key(resourceId)] = { serverVersion, fields: copy(fields) }
  }

  function load(resourceId: string | null, serverVersion: number | null): ArticleDraftFields | undefined {
    const draft = drafts.value[key(resourceId)]
    return draft?.serverVersion === serverVersion ? copy(draft.fields) : undefined
  }

  function clear(resourceId: string | null) {
    const next = { ...drafts.value }
    delete next[key(resourceId)]
    drafts.value = next
  }

  return { save, load, clear }
})
