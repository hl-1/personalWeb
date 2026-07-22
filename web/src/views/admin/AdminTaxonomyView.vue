<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { taxonomyCreateSchema, taxonomyUpdateSchema } from '../../features/admin/taxonomy/taxonomy-form-schema'
import { createAdminFormValidation, focusAdminField } from '../../features/admin/admin-form-validation'
import AdminFormField from '../../features/admin/components/AdminFormField.vue'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import { useAdminOperationFeedback, type AdminOperation } from '../../features/admin/admin-operation-feedback'
import { confirmAdminAction } from '../../features/admin/admin-confirmation'

const props = defineProps<{ adminClient: AdminClient; kind: 'category' | 'tag' }>()
const feedback = useAdminOperationFeedback()
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
const createValidation = createAdminFormValidation(taxonomyCreateSchema)
const editValidation = createAdminFormValidation(taxonomyUpdateSchema)
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
  editValidation.resetValidation()
}

function beginEditById(id: string) {
  const row = rows.data.value?.find(item => item.id === id)
  if (row) beginEdit(row)
}

function cancelEdit() {
  editingId.value = undefined
  errorMessage.value = undefined
  editValidation.resetValidation()
}

function conflictMessage(error: unknown): string {
  if (!(error instanceof AdminApiError)) return 'Unable to save changes'
  if (error.code === 'duplicate_slug') return 'Slug is already in use'
  if (error.code === 'stale_version') return 'Reload this row before saving again'
  if (error.code === 'taxonomy_in_use') return 'Remove article references first'
  return 'Unable to save changes'
}

function operationFor(action: 'create' | 'update' | 'delete'): AdminOperation {
  if (props.kind === 'category') {
    if (action === 'create') return 'category.create'
    if (action === 'update') return 'category.update'
    return 'category.delete'
  }
  if (action === 'create') return 'tag.create'
  if (action === 'update') return 'tag.update'
  return 'tag.delete'
}

async function createRow() {
  if (saving.value) return
  errorMessage.value = undefined
  const result = createValidation.validateForSubmit({ ...createForm }, ['name', 'slug'])
  if (!result.success) {
    errorMessage.value = 'Review the highlighted fields'
    await focusAdminField(result.firstInvalidField)
    return
  }
  saving.value = true
  try {
    const input = result.data
    if (props.kind === 'category') await props.adminClient.createCategory(input)
    else await props.adminClient.createTag(input)
    feedback.succeeded(operationFor('create'))
    Object.assign(createForm, { name: '', slug: '' })
    createValidation.resetValidation()
    await rows.refetch()
  } catch (error) {
    feedback.failed(operationFor('create'))
    if (error instanceof AdminApiError && error.fieldErrors) {
      errorMessage.value = 'Review the highlighted fields'
      await focusAdminField(createValidation.applyServerErrors(error.fieldErrors))
    } else errorMessage.value = conflictMessage(error)
  } finally {
    saving.value = false
  }
}

async function saveRow() {
  if (!editingId.value || saving.value) return
  errorMessage.value = undefined
  const result = editValidation.validateForSubmit({ ...editForm }, ['name', 'slug'])
  if (!result.success) {
    errorMessage.value = 'Review the highlighted fields'
    await focusAdminField(result.firstInvalidField === 'name' ? 'editName' : 'editSlug')
    return
  }
  saving.value = true
  try {
    const input = result.data
    if (props.kind === 'category') await props.adminClient.updateCategory(editingId.value, input)
    else await props.adminClient.updateTag(editingId.value, input)
    feedback.succeeded(operationFor('update'))
    editingId.value = undefined
    await rows.refetch()
  } catch (error) {
    feedback.failed(operationFor('update'))
    if (error instanceof AdminApiError && error.fieldErrors) {
      errorMessage.value = 'Review the highlighted fields'
      const field = editValidation.applyServerErrors(error.fieldErrors)
      await focusAdminField(field === 'name' ? 'editName' : 'editSlug')
    } else errorMessage.value = conflictMessage(error)
  } finally {
    saving.value = false
  }
}

async function deleteRow(row: { id: string; name: string; articleCount: number; version: number }) {
  if (saving.value) return
  const result = await confirmAdminAction({
    title: `删除${props.kind === 'category' ? '分类' : '标签'}`,
    message: `确定删除 ${row.name} 吗？当前有 ${row.articleCount} 篇文章引用。`,
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  if (result !== 'confirmed') return
  saving.value = true
  errorMessage.value = undefined
  try {
    if (props.kind === 'category') await props.adminClient.deleteCategory(row.id, row.version)
    else await props.adminClient.deleteTag(row.id, row.version)
    feedback.succeeded(operationFor('delete'))
    await rows.refetch()
  } catch (error) {
    feedback.failed(operationFor('delete'))
    errorMessage.value = conflictMessage(error)
  } finally {
    saving.value = false
  }
}

async function deleteRowById(id: string) {
  const row = rows.data.value?.find(item => item.id === id)
  if (row) await deleteRow(row)
}
</script>

<template>
  <section>
    <header class="admin-page-heading">
      <p>Content</p>
      <h1>{{ title }}</h1>
    </header>

    <el-form
      class="create-row"
      @submit.prevent="createRow"
    >
      <AdminFormField
        name="name"
        label="Name"
        required
        hint="Required; up to 120 characters"
        :errors="createValidation.errorsFor('name')"
      >
        <template #default="{controlId,describedBy,invalid}">
          <el-input
            :id="controlId"
            v-model="createForm.name"
            name="name"
            maxlength="120"
            :aria-describedby="describedBy"
            :aria-invalid="invalid"
            @blur="createValidation.touch('name',{...createForm})"
            @input="createValidation.change('name',{...createForm})"
          />
        </template>
      </AdminFormField>
      <AdminFormField
        name="slug"
        label="Slug"
        required
        hint="3-120 lowercase letters, numbers, and single hyphens"
        :errors="createValidation.errorsFor('slug')"
      >
        <template #default="{controlId,describedBy,invalid}">
          <el-input
            :id="controlId"
            v-model="createForm.slug"
            name="slug"
            maxlength="120"
            :aria-describedby="describedBy"
            :aria-invalid="invalid"
            @blur="createValidation.touch('slug',{...createForm})"
            @input="createValidation.change('slug',{...createForm})"
          />
        </template>
      </AdminFormField>
      <el-button
        type="primary"
        native-type="submit"
        :disabled="saving"
      >
        Add {{ kind }}
      </el-button>
    </el-form>

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
      <el-table
        class="taxonomy-list"
        :data="rows.data.value ?? []"
        row-key="id"
      >
        <el-table-column
          label="Taxonomy"
          min-width="420"
        >
          <template #default="{ row }">
            <el-form
              v-if="editingId === row.id"
              class="row-editor"
              data-testid="save-taxonomy"
              @submit.prevent="saveRow"
            >
              <AdminFormField
                name="editName"
                label="Name"
                required
                hint="Required; up to 120 characters"
                :errors="editValidation.errorsFor('name')"
              >
                <template #default="{controlId,describedBy,invalid}">
                  <el-input
                    :id="controlId"
                    v-model="editForm.name"
                    name="editName"
                    maxlength="120"
                    :aria-describedby="describedBy"
                    :aria-invalid="invalid"
                    @blur="editValidation.touch('name',{...editForm})"
                    @input="editValidation.change('name',{...editForm})"
                  />
                </template>
              </AdminFormField>
              <AdminFormField
                name="editSlug"
                label="Slug"
                required
                hint="3-120 lowercase letters, numbers, and single hyphens"
                :errors="editValidation.errorsFor('slug')"
              >
                <template #default="{controlId,describedBy,invalid}">
                  <el-input
                    :id="controlId"
                    v-model="editForm.slug"
                    name="editSlug"
                    maxlength="120"
                    :aria-describedby="describedBy"
                    :aria-invalid="invalid"
                    @blur="editValidation.touch('slug',{...editForm})"
                    @input="editValidation.change('slug',{...editForm})"
                  />
                </template>
              </AdminFormField>
              <div class="row-actions">
                <el-button
                  type="primary"
                  native-type="submit"
                  :disabled="saving"
                >
                  Save
                </el-button>
                <el-button
                  :disabled="saving"
                  @click="cancelEdit"
                >
                  Cancel
                </el-button>
              </div>
            </el-form>
            <div
              v-else
              class="row-name"
            >
              <strong>{{ row.name }}</strong><span>{{ row.slug }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="References"
          width="130"
        >
          <template #default="{ row }">
            {{ row.articleCount }} refs
          </template>
        </el-table-column>
        <el-table-column
          label="Actions"
          width="180"
          fixed="right"
        >
          <template #default="{ row }">
            <div
              v-if="editingId !== row.id"
              class="row-actions"
            >
              <el-button
                data-testid="edit-taxonomy"
                size="small"
                :disabled="saving"
                @click="beginEditById(row.id)"
              >
                Edit
              </el-button>
              <el-button
                data-testid="delete-taxonomy"
                size="small"
                type="danger"
                plain
                :disabled="saving"
                @click="deleteRowById(row.id)"
              >
                Delete
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
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
