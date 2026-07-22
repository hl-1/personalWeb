<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminArticleInput, type AdminClient } from '../../features/admin/admin-client'
import { articleDetailQueryKey, createCategoryQueryOptions, createTagQueryOptions } from '../../features/admin/admin-query'
import type { AdminArticle } from '../../features/admin/admin-schema'
import { articleFormSchema, type ArticleForm } from '../../features/admin/article/article-form-schema'
import { useArticleDraftStore, type ArticleDraftFields } from '../../features/admin/article/article-draft-store'
import { createAdminFormValidation, focusAdminField } from '../../features/admin/admin-form-validation'
import AdminFormField from '../../features/admin/components/AdminFormField.vue'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'
import { useAdminOperationFeedback } from '../../features/admin/admin-operation-feedback'
import { confirmAdminAction } from '../../features/admin/admin-confirmation'

const props = defineProps<{ adminClient: AdminClient; id?: string }>()
const feedback = useAdminOperationFeedback()
const router = useRouter()
const drafts = useArticleDraftStore()
const isNew = computed(() => props.id === undefined)
const initialized = ref(false)
const dirty = ref(false)
const saving = ref(false)
const stale = ref(false)
const formError = ref<string | undefined>()
const scheduleValue = ref('')
const currentStatus = ref<AdminArticle['status']>()

function emptyArticleForm(): ArticleForm {
  return {
    slug: '', title: '', summary: '', bodyMarkdown: '',
    categoryId: null, tagIds: [],
    seoTitle: null, seoDescription: null,
    version: null, publishMode: 'draft', publishAt: null,
  }
}

const form = reactive<ArticleForm>(emptyArticleForm())
const categorySelection = computed({
  get: () => form.categoryId ?? '',
  set: (value: string) => {
    form.categoryId = value || null
  },
})
const validation = createAdminFormValidation(articleFormSchema)
const fieldOrder = ['title', 'slug', 'summary', 'tagIds', 'bodyMarkdown', 'seoTitle', 'seoDescription', 'publishAt']

function publishAtValue() {
  if (form.publishMode !== 'scheduled' || !scheduleValue.value) return null
  const value = new Date(scheduleValue.value)
  return Number.isNaN(value.valueOf()) ? 'invalid' : value.toISOString()
}

function validationInput() {
  return { ...form, tagIds: [...form.tagIds], publishAt: publishAtValue() }
}

const article = useQuery({
  queryKey: computed(() => articleDetailQueryKey(props.id ?? 'new')),
  queryFn: () => props.adminClient.getArticle(props.id ?? ''),
  enabled: computed(() => !isNew.value),
  retry: false,
})
const categories = useQuery(createCategoryQueryOptions(props.adminClient))
const tags = useQuery(createTagQueryOptions(props.adminClient))

function draftFields(): ArticleDraftFields {
  return {
    slug: form.slug,
    title: form.title,
    summary: form.summary,
    bodyMarkdown: form.bodyMarkdown,
    categoryId: form.categoryId,
    tagIds: [...form.tagIds],
    seoTitle: form.seoTitle,
    seoDescription: form.seoDescription,
  }
}

function assignFields(fields: ArticleDraftFields) {
  Object.assign(form, { ...fields, tagIds: [...fields.tagIds] })
}

function applyArticle(value: AdminArticle) {
  currentStatus.value = value.status
  const serverFields: ArticleDraftFields = {
    slug: value.slug,
    title: value.title,
    summary: value.summary,
    bodyMarkdown: value.bodyMarkdown,
    categoryId: value.categoryId,
    tagIds: [...value.tagIds],
    seoTitle: value.seoTitle,
    seoDescription: value.seoDescription,
  }
  form.version = value.version
  form.publishMode = 'draft'
  form.publishAt = null
  assignFields(drafts.load(value.id, value.version) ?? serverFields)
  initialized.value = true
  dirty.value = drafts.load(value.id, value.version) !== undefined
  validation.resetValidation()
}

if (isNew.value) {
  const draft = drafts.load(null, null)
  if (draft) assignFields(draft)
  initialized.value = true
  dirty.value = draft !== undefined
}

watch(article.data, (value) => {
  if (value && (!initialized.value || !dirty.value)) applyArticle(value)
}, { immediate: true })

watch(form, () => {
  if (!initialized.value || saving.value) return
  dirty.value = true
  drafts.save(props.id ?? null, form.version, draftFields())
}, { deep: true })

const pageState = computed<'loading' | 'error' | 'unauthorized' | 'forbidden' | 'ready'>(() => {
  if (!isNew.value && article.isPending.value) return 'loading'
  const error = article.error.value
  if (error instanceof AdminApiError && error.kind === 'unauthorized') return 'unauthorized'
  if (error instanceof AdminApiError && error.kind === 'forbidden') return 'forbidden'
  if (article.isError.value || categories.isError.value || tags.isError.value) return 'error'
  return 'ready'
})

function requestInput(parsed: ArticleForm): AdminArticleInput {
  return {
    slug: parsed.slug,
    title: parsed.title,
    summary: parsed.summary,
    bodyMarkdown: parsed.bodyMarkdown,
    categoryId: parsed.categoryId,
    tagIds: [...parsed.tagIds],
    seoTitle: parsed.seoTitle,
    seoDescription: parsed.seoDescription,
  }
}
async function resetForNextArticle() {
  Object.assign(form, emptyArticleForm())
  scheduleValue.value = ''
  currentStatus.value = undefined
  formError.value = undefined
  stale.value = false
  validation.resetValidation()

  // 确保 watch 在 saving=true 时处理完本次表单变化，避免生成空白草稿。
  await nextTick()
  drafts.clear(null)
  dirty.value = false
}

async function save() {
  if (saving.value) return
  formError.value = undefined
  stale.value = false

  const result = validation.validateForSubmit(validationInput(), fieldOrder)
  if (!result.success) {
    formError.value = 'Review the highlighted fields'
    await focusAdminField(result.firstInvalidField)
    return
  }

  saving.value = true
  const operation = isNew.value ? 'article.create' : 'article.update'
  let mutationSucceeded = false

  try {
    const parsed = result.data

    if (isNew.value) {
      await props.adminClient.createArticle(requestInput(parsed))
      mutationSucceeded = true

      const continueAdding = await confirmAdminAction({
        title: '文章添加成功',
        message: '是否继续添加下一篇文章？',
        confirmButtonText: '继续添加',
        cancelButtonText: '返回列表',
        type: 'success',
      })
      feedback.succeeded('article.create')
      await nextTick()

      drafts.clear(null)
      dirty.value = false

      if (continueAdding === 'cancelled') {
        await router.replace('/admin/articles')
      } else {
        await resetForNextArticle()
      }
      return
    }

    const saved = await props.adminClient.updateArticle(props.id ?? '', {
      ...requestInput(parsed),
      version: parsed.version ?? 0,
    })
    mutationSucceeded = true
    drafts.clear(props.id ?? null)
    applyArticle(saved)
    dirty.value = false
    feedback.succeeded('article.update')
  } catch (error) {
    if (mutationSucceeded) {
      formError.value = 'Article was added, but the next page could not be opened'
      return
    }

    feedback.failed(operation)
    if (error instanceof AdminApiError && error.code === 'stale_version') {
      stale.value = true
    } else if (error instanceof AdminApiError && error.fieldErrors) {
      formError.value = 'Review the highlighted fields'
      await focusAdminField(validation.applyServerErrors(error.fieldErrors))
    } else {
      formError.value = 'Unable to save article'
    }
  } finally {
    saving.value = false
  }
}

async function reloadServerVersion() {
  const result = await article.refetch()
  if (result.data) {
    drafts.clear(props.id ?? null)
    dirty.value = false
    stale.value = false
    applyArticle(result.data)
  }
}

function discardDraft() {
  drafts.clear(props.id ?? null)
  dirty.value = false
  if (article.data.value) applyArticle(article.data.value)
  else assignFields({ slug: '', title: '', summary: '', bodyMarkdown: '', categoryId: null,
    tagIds: [], seoTitle: null, seoDescription: null })
  validation.resetValidation()
}

async function changeState(action: 'publish' | 'archive') {
  if (!props.id || form.version === null || saving.value) return
  if (action === 'publish' && form.publishMode === 'scheduled') {
    validation.touch('publishAt', validationInput())
    if (validation.errorsFor('publishAt').length > 0) {
      formError.value = 'Review the highlighted fields'
      await focusAdminField('publishAt')
      return
    }
  }
  saving.value = true
  const operation = action === 'publish' ? 'article.publish' : 'article.archive'
  try {
    const publishAt = form.publishMode === 'scheduled' && scheduleValue.value
      ? new Date(scheduleValue.value).toISOString()
      : null
    const updated = action === 'publish'
      ? await props.adminClient.publishArticle(props.id, form.version, publishAt)
      : await props.adminClient.archiveArticle(props.id, form.version)
    applyArticle(updated)
    dirty.value = false
    feedback.succeeded(operation)
  } catch {
    feedback.failed(operation)
    formError.value = action === 'publish' ? 'Unable to publish article' : 'Unable to archive article'
  } finally {
    saving.value = false
  }
}

onBeforeRouteLeave(async () => {
  if (!dirty.value) return true
  return await confirmAdminAction({
    title: '放弃未保存更改',
    message: '确定离开并放弃当前文章修改吗？',
    confirmButtonText: '放弃更改',
    cancelButtonText: '继续编辑',
    type: 'warning',
  }) === 'confirmed'
})
</script>

<template>
  <section>
    <header class="admin-page-heading">
      <div>
        <p>Articles</p>
        <h1>{{ isNew ? 'New article' : 'Edit article' }}</h1>
      </div>
      <RouterLink to="/admin/articles">
        Back to articles
      </RouterLink>
    </header>

    <AdminPageState
      :state="pageState"
      @retry="article.refetch()"
    >
      <el-form
        data-testid="save-article"
        class="article-form"
        @submit.prevent="save"
      >
        <div
          v-if="stale"
          class="form-alert"
          role="alert"
        >
          <span>The server version changed. Your input is still here.</span>
          <el-button
            @click="reloadServerVersion"
          >
            Reload server version
          </el-button>
        </div>
        <p
          v-if="formError"
          class="form-alert"
          role="alert"
        >
          {{ formError }}
        </p>

        <div class="form-grid">
          <AdminFormField
            class="wide"
            name="title"
            label="Title"
            required
            hint="Required; up to 180 characters"
            :errors="validation.errorsFor('title')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-input
                :id="controlId"
                v-model="form.title"
                name="title"
                maxlength="180"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('title', validationInput())"
                @input="validation.change('title', validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="slug"
            label="Slug"
            required
            hint="3-120 lowercase letters, numbers, and single hyphens"
            :errors="validation.errorsFor('slug')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-input
                :id="controlId"
                v-model="form.slug"
                name="slug"
                maxlength="120"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('slug', validationInput())"
                @input="validation.change('slug', validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="categoryId"
            label="Category"
            hint="Optional"
            :errors="validation.errorsFor('categoryId')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-select
                :id="controlId"
                v-model="categorySelection"
                name="categoryId"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('categoryId', validationInput())"
                @change="validation.change('categoryId', validationInput())"
              >
                <el-option
                  label="None"
                  value=""
                />
                <el-option
                  v-for="item in categories.data.value"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </template>
          </AdminFormField>
          <AdminFormField
            class="wide"
            name="summary"
            label="Summary"
            required
            hint="Required; up to 500 characters"
            :errors="validation.errorsFor('summary')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-input
                :id="controlId"
                v-model="form.summary"
                name="summary"
                type="textarea"
                :rows="3"
                maxlength="500"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('summary', validationInput())"
                @input="validation.change('summary', validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            class="wide"
            name="tagIds"
            label="Tags"
            hint="Optional; select up to 10 tags"
            :errors="validation.errorsFor('tagIds')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-select
                :id="controlId"
                v-model="form.tagIds"
                name="tagIds"
                multiple
                collapse-tags
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('tagIds', validationInput())"
                @change="validation.change('tagIds', validationInput())"
              >
                <el-option
                  v-for="item in tags.data.value"
                  :key="item.id"
                  :label="item.name"
                  :value="item.id"
                />
              </el-select>
            </template>
          </AdminFormField>
          <AdminFormField
            class="wide"
            name="bodyMarkdown"
            label="Body"
            hint="Optional; up to 200,000 characters"
            :errors="validation.errorsFor('bodyMarkdown')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <MarkdownEditor
                :model-value="form.bodyMarkdown"
                name="bodyMarkdown"
                :control-id="controlId"
                :described-by="describedBy"
                :invalid="invalid"
                :preview="adminClient.previewArticle"
                @update:model-value="form.bodyMarkdown = $event; validation.change('bodyMarkdown', validationInput())"
                @blur="validation.touch('bodyMarkdown', validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="seoTitle"
            label="SEO title"
            hint="Optional; up to 70 characters"
            :errors="validation.errorsFor('seoTitle')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-input
                :id="controlId"
                v-model="form.seoTitle"
                name="seoTitle"
                maxlength="70"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('seoTitle', validationInput())"
                @input="validation.change('seoTitle', validationInput())"
              />
            </template>
          </AdminFormField>
          <AdminFormField
            name="seoDescription"
            label="SEO description"
            hint="Optional; up to 160 characters"
            :errors="validation.errorsFor('seoDescription')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-input
                :id="controlId"
                v-model="form.seoDescription"
                name="seoDescription"
                maxlength="160"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('seoDescription', validationInput())"
                @input="validation.change('seoDescription', validationInput())"
              />
            </template>
          </AdminFormField>
        </div>

        <fieldset v-if="!isNew && currentStatus === 'DRAFT'">
          <legend>Publication</legend>
          <el-radio-group v-model="form.publishMode">
            <el-radio value="now">
              Publish now
            </el-radio>
            <el-radio value="scheduled">
              Schedule
            </el-radio>
          </el-radio-group>
          <AdminFormField
            v-if="form.publishMode === 'scheduled'"
            name="publishAt"
            label="Publication time"
            required
            hint="Required when scheduling; choose a local date and time"
            :errors="validation.errorsFor('publishAt')"
          >
            <template #default="{ controlId, describedBy, invalid }">
              <el-date-picker
                :id="controlId"
                v-model="scheduleValue"
                name="publishAt"
                type="datetime"
                value-format="YYYY-MM-DDTHH:mm"
                format="YYYY-MM-DD HH:mm"
                :aria-describedby="describedBy"
                :aria-invalid="invalid"
                @blur="validation.touch('publishAt', validationInput())"
                @input="validation.change('publishAt', validationInput())"
              />
            </template>
          </AdminFormField>
          <el-button
            type="primary"
            :disabled="saving"
            @click="changeState('publish')"
          >
            Publish
          </el-button>
        </fieldset>

        <div class="form-actions">
          <el-button
            type="primary"
            native-type="submit"
            :disabled="saving"
          >
            {{ saving ? 'Saving' : 'Save' }}
          </el-button>
          <el-button
            :disabled="saving"
            @click="discardDraft"
          >
            Discard draft
          </el-button>
          <el-button
            v-if="currentStatus === 'PUBLISHED'"
            :disabled="saving"
            @click="changeState('archive')"
          >
            Archive
          </el-button>
        </div>
      </el-form>
    </AdminPageState>
  </section>
</template>

<style scoped>
.admin-page-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 26px; }
.admin-page-heading p { margin: 0 0 5px; color: #2f7758; font-size: 12px; font-weight: 750; text-transform: uppercase; }
.admin-page-heading h1 { margin: 0; font-size: 30px; overflow-wrap: anywhere; }
.article-form { min-width: 0; }
.form-alert { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: 0 0 18px; padding: 12px 14px; border-left: 3px solid #a33c34; background: #fff4f2; color: #76261f; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
label { display: grid; min-width: 0; gap: 6px; color: #59645d; font-size: 12px; font-weight: 700; }
.wide { grid-column: 1 / -1; }
input, textarea, select { width: 100%; min-width: 0; padding: 9px 10px; border: 1px solid #aeb8b1; border-radius: 4px; background: #fff; font: inherit; }
select[multiple] { min-height: 120px; }
fieldset { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 22px 0 0; padding: 14px; border: 1px solid #d8dfda; }
fieldset label { display: flex; align-items: center; gap: 6px; }
fieldset input { width: auto; }
.form-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 24px; padding-top: 18px; border-top: 1px solid #d8dfda; }
.form-actions .el-button,
fieldset .el-button,
.form-alert .el-button {
  min-height: 38px;
}
@media (max-width: 680px) {
  .admin-page-heading { align-items: flex-start; flex-direction: column; }
  .form-grid { grid-template-columns: minmax(0, 1fr); }
  .wide { grid-column: auto; }
  .form-alert { align-items: flex-start; flex-direction: column; }
}
</style>
