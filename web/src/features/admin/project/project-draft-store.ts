import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ProjectDraftFields {
  slug: string; title: string; summary: string; descriptionMarkdown: string
  projectUrl: string | null; repositoryUrl: string | null; featured: boolean; sortOrder: number
}
interface Stored { serverVersion: number | null; fields: ProjectDraftFields }
const key = (id: string | null) => id === null ? 'new' : `resource:${id}`
const copy = (fields: ProjectDraftFields): ProjectDraftFields => ({ ...fields })

export const useProjectDraftStore = defineStore('admin-project-drafts', () => {
  const drafts = ref<Record<string, Stored>>({})
  function save(id: string | null, serverVersion: number | null, fields: ProjectDraftFields) {
    drafts.value[key(id)] = { serverVersion, fields: copy(fields) }
  }
  function load(id: string | null, serverVersion: number | null) {
    const draft = drafts.value[key(id)]
    return draft?.serverVersion === serverVersion ? copy(draft.fields) : undefined
  }
  function clear(id: string | null) {
    const next = { ...drafts.value }; delete next[key(id)]; drafts.value = next
  }
  return { save, load, clear }
})
