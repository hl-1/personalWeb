<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { parseTaxonomyCreateForm, parseTaxonomyUpdateForm } from '../../features/admin/taxonomy/taxonomy-form-schema'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'

const props = defineProps<{ adminClient: AdminClient; kind: 'category' | 'tag' }>()
const title = computed(() => props.kind === 'category' ? 'Categories' : 'Tags')
const rows = useQuery({
  queryKey: computed(() => ['admin', props.kind === 'category' ? 'categories' : 'tags'] as const),
  queryFn: () => props.kind === 'category'
    ? props.adminClient.listCategories()
    : props.adminClient.listTags(),
  retry: false,
})
const createForm = reactive({ name: '', slug: '' })
const editForm = reactive({ name: '', slug: '', version: 0 })
const editingId = ref<string | undefined>()
const saving = ref(false)
const errorMessage = ref<string | undefined>()

const state = computed<'loading' | 'empty' | 'error' | 'unauthorized' | 'forbidden' | 'ready'>(() => {
  if (rows.isPending.value) return 'loading'
  const error = rows.error.value
  if (error instanceof AdminApiError && error.kind === 'unauthorized') return 'unauthorized'
  if (error instanceof AdminApiError && error.kind === 'forbidden') return 'forbidden'
  if (rows.isError.value) return 'error'
  return rows.data.value?.length === 0 ? 'empty' : 'ready'
})

function beginEdit(row: { id: string; name: string; slug: string; version: number }) {
  editingId.value = row.id
  Object.assign(editForm, { name: row.name, slug: row.slug, version: row.version })
  errorMessage.value = undefined
}

function cancelEdit() {
  editingId.value = undefined
  errorMessage.value = undefined
}

function conflictMessage(error: unknown): string {
  if (!(error instanceof AdminApiError)) return 'Unable to save changes'
  if (error.code === 'duplicate_slug') return 'Slug is already in use'
  if (error.code === 'stale_version') return 'Reload this row before saving again'
  if (error.code === 'taxonomy_in_use') return 'Remove article references first'
  return 'Unable to save changes'
}

async function createRow() {
  if (saving.value) return
  saving.value = true
  errorMessage.value = undefined
  try {
    const input = parseTaxonomyCreateForm(createForm)
    if (props.kind === 'category') await props.adminClient.createCategory(input)
    else await props.adminClient.createTag(input)
    Object.assign(createForm, { name: '', slug: '' })
    await rows.refetch()
  } catch (error) {
    errorMessage.value = conflictMessage(error)
  } finally {
    saving.value = false
  }
}

async function saveRow() {
  if (!editingId.value || saving.value) return
  saving.value = true
  errorMessage.value = undefined
  try {
    const input = parseTaxonomyUpdateForm(editForm)
    if (props.kind === 'category') await props.adminClient.updateCategory(editingId.value, input)
    else await props.adminClient.updateTag(editingId.value, input)
    editingId.value = undefined
    await rows.refetch()
  } catch (error) {
    errorMessage.value = conflictMessage(error)
  } finally {
    saving.value = false
  }
}

async function deleteRow(row: { id: string; name: string; articleCount: number; version: number }) {
  if (saving.value || !globalThis.confirm(
    `Delete ${row.name}? It is referenced by ${row.articleCount} article(s).`,
  )) return
  saving.value = true
  errorMessage.value = undefined
  try {
    if (props.kind === 'category') await props.adminClient.deleteCategory(row.id, row.version)
    else await props.adminClient.deleteTag(row.id, row.version)
    await rows.refetch()
  } catch (error) {
    errorMessage.value = conflictMessage(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <section>
    <header class="admin-page-heading">
      <p>Content</p>
      <h1>{{ title }}</h1>
    </header>

    <form
      class="create-row"
      @submit.prevent="createRow"
    >
      <label><span>Name</span><input
        v-model="createForm.name"
        name="name"
        maxlength="120"
      ></label>
      <label><span>Slug</span><input
        v-model="createForm.slug"
        name="slug"
        maxlength="120"
      ></label>
      <button
        type="submit"
        :disabled="saving"
      >
        Add {{ kind }}
      </button>
    </form>

    <p
      v-if="errorMessage"
      class="taxonomy-error"
      role="alert"
    >
      {{ errorMessage }}
    </p>

    <AdminPageState
      :state="state"
      :empty-label="`No ${title.toLowerCase()}`"
      @retry="rows.refetch()"
    >
      <div class="taxonomy-list">
        <div
          v-for="row in rows.data.value"
          :key="row.id"
          class="taxonomy-row"
        >
          <form
            v-if="editingId === row.id"
            class="row-editor"
            data-testid="save-taxonomy"
            @submit.prevent="saveRow"
          >
            <input
              v-model="editForm.name"
              name="editName"
              maxlength="120"
              aria-label="Name"
            >
            <input
              v-model="editForm.slug"
              name="editSlug"
              maxlength="120"
              aria-label="Slug"
            >
            <span>{{ row.articleCount }} refs</span>
            <div class="row-actions">
              <button
                type="submit"
                :disabled="saving"
              >
                Save
              </button>
              <button
                type="button"
                :disabled="saving"
                @click="cancelEdit"
              >
                Cancel
              </button>
            </div>
          </form>
          <template v-else>
            <div class="row-name">
              <strong>{{ row.name }}</strong><span>{{ row.slug }}</span>
            </div>
            <span>{{ row.articleCount }} refs</span>
            <div class="row-actions">
              <button
                data-testid="edit-taxonomy"
                type="button"
                :disabled="saving"
                @click="beginEdit(row)"
              >
                Edit
              </button>
              <button
                data-testid="delete-taxonomy"
                type="button"
                :disabled="saving"
                @click="deleteRow(row)"
              >
                Delete
              </button>
            </div>
          </template>
        </div>
      </div>
    </AdminPageState>
  </section>
</template>

<style scoped>
.admin-page-heading { margin-bottom: 22px; }
.admin-page-heading p { margin: 0 0 5px; color: #2f7758; font-size: 12px; font-weight: 750; text-transform: uppercase; }
.admin-page-heading h1 { margin: 0; font-size: 30px; overflow-wrap: anywhere; }
.create-row { display: grid; grid-template-columns: minmax(140px, 1fr) minmax(140px, 1fr) auto; align-items: end; gap: 10px; margin-bottom: 20px; }
label { display: grid; gap: 5px; color: #59645d; font-size: 12px; font-weight: 700; }
input, button { min-height: 38px; padding: 7px 10px; border: 1px solid #aeb8b1; border-radius: 4px; background: #fff; font: inherit; }
button { cursor: pointer; }
.taxonomy-error { padding: 11px 13px; border-left: 3px solid #a33c34; background: #fff4f2; color: #76261f; }
.taxonomy-list { border-top: 1px solid #d8dfda; }
.taxonomy-row, .row-editor { display: grid; grid-template-columns: minmax(0, 1fr) 100px auto; align-items: center; gap: 14px; min-width: 0; padding: 13px 4px; border-bottom: 1px solid #d8dfda; }
.row-editor { grid-template-columns: minmax(120px, 1fr) minmax(120px, 1fr) 80px auto; padding: 0; border: 0; }
.row-name { min-width: 0; overflow-wrap: anywhere; }
.row-name span { display: block; margin-top: 3px; color: #69746d; font-size: 12px; }
.row-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
@media (max-width: 680px) {
  .create-row, .taxonomy-row, .row-editor { grid-template-columns: minmax(0, 1fr); }
  .row-actions { justify-content: flex-start; }
}
</style>
