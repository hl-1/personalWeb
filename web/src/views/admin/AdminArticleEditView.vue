<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminArticleInput, type AdminClient } from '../../features/admin/admin-client'
import { articleDetailQueryKey, createCategoryQueryOptions, createTagQueryOptions } from '../../features/admin/admin-query'
import type { AdminArticle } from '../../features/admin/admin-schema'
import { parseArticleForm, type ArticleForm } from '../../features/admin/article/article-form-schema'
import { useArticleDraftStore, type ArticleDraftFields } from '../../features/admin/article/article-draft-store'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import MarkdownEditor from '../../features/admin/components/MarkdownEditor.vue'

const props = defineProps<{ adminClient: AdminClient; id?: string }>()
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

const form = reactive<ArticleForm>({
  slug: '', title: '', summary: '', bodyMarkdown: '', categoryId: null, tagIds: [],
  seoTitle: null, seoDescription: null, version: null, publishMode: 'draft', publishAt: null,
})

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

async function focusField(fieldErrors: Record<string, string[]> | undefined) {
  const field = fieldErrors ? Object.keys(fieldErrors)[0] : undefined
  if (!field) return
  await nextTick()
  document.querySelector<HTMLElement>(`[name="${field}"]`)?.focus()
}

async function save() {
  if (saving.value) return
  formError.value = undefined
  stale.value = false
  saving.value = true
  try {
    const publishAt = form.publishMode === 'scheduled' && scheduleValue.value
      ? new Date(scheduleValue.value).toISOString()
      : null
    const parsed = parseArticleForm({ ...form, publishAt })
    let saved: AdminArticle
    if (isNew.value) {
      saved = (await props.adminClient.createArticle(requestInput(parsed))).data
      drafts.clear(null)
      dirty.value = false
      await router.replace(`/admin/articles/${saved.id}`)
    } else {
      saved = await props.adminClient.updateArticle(props.id ?? '', {
        ...requestInput(parsed),
        version: parsed.version ?? 0,
      })
      drafts.clear(props.id ?? null)
    }
    applyArticle(saved)
    dirty.value = false
  } catch (error) {
    if (error instanceof AdminApiError && error.code === 'stale_version') {
      stale.value = true
    } else if (error instanceof AdminApiError && error.fieldErrors) {
      formError.value = 'Review the highlighted fields'
      await focusField(error.fieldErrors)
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
}

async function changeState(action: 'publish' | 'archive') {
  if (!props.id || form.version === null || saving.value) return
  saving.value = true
  try {
    const publishAt = form.publishMode === 'scheduled' && scheduleValue.value
      ? new Date(scheduleValue.value).toISOString()
      : null
    const updated = action === 'publish'
      ? await props.adminClient.publishArticle(props.id, form.version, publishAt)
      : await props.adminClient.archiveArticle(props.id, form.version)
    applyArticle(updated)
    dirty.value = false
  } finally {
    saving.value = false
  }
}

onBeforeRouteLeave(() => !dirty.value || globalThis.confirm('Discard unsaved article changes?'))
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
      <form
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
          <button
            type="button"
            @click="reloadServerVersion"
          >
            Reload server version
          </button>
        </div>
        <p
          v-if="formError"
          class="form-alert"
          role="alert"
        >
          {{ formError }}
        </p>

        <div class="form-grid">
          <label class="wide"><span>Title</span><input
            v-model="form.title"
            name="title"
            maxlength="180"
          ></label>
          <label><span>Slug</span><input
            v-model="form.slug"
            name="slug"
            maxlength="120"
          ></label>
          <label><span>Category</span><select
            v-model="form.categoryId"
            name="categoryId"
          >
            <option :value="null">None</option>
            <option
              v-for="item in categories.data.value"
              :key="item.id"
              :value="item.id"
            >{{ item.name }}</option>
          </select></label>
          <label class="wide"><span>Summary</span><textarea
            v-model="form.summary"
            name="summary"
            rows="3"
            maxlength="500"
          /></label>
          <label class="wide"><span>Tags</span><select
            v-model="form.tagIds"
            name="tagIds"
            multiple
            size="5"
          >
            <option
              v-for="item in tags.data.value"
              :key="item.id"
              :value="item.id"
            >{{ item.name }}</option>
          </select></label>
          <label class="wide"><span>Body</span><MarkdownEditor
            v-model="form.bodyMarkdown"
            :preview="adminClient.previewArticle"
          /></label>
          <label><span>SEO title</span><input
            v-model="form.seoTitle"
            name="seoTitle"
            maxlength="70"
          ></label>
          <label><span>SEO description</span><input
            v-model="form.seoDescription"
            name="seoDescription"
            maxlength="160"
          ></label>
        </div>

        <fieldset v-if="!isNew && currentStatus === 'DRAFT'">
          <legend>Publication</legend>
          <label><input
            v-model="form.publishMode"
            type="radio"
            value="now"
          > Publish now</label>
          <label><input
            v-model="form.publishMode"
            type="radio"
            value="scheduled"
          > Schedule</label>
          <input
            v-if="form.publishMode === 'scheduled'"
            v-model="scheduleValue"
            type="datetime-local"
          >
          <button
            type="button"
            :disabled="saving"
            @click="changeState('publish')"
          >
            Publish
          </button>
        </fieldset>

        <div class="form-actions">
          <button
            class="primary-command"
            type="submit"
            :disabled="saving"
          >
            {{ saving ? 'Saving' : 'Save' }}
          </button>
          <button
            type="button"
            :disabled="saving"
            @click="discardDraft"
          >
            Discard draft
          </button>
          <button
            v-if="currentStatus === 'PUBLISHED'"
            type="button"
            :disabled="saving"
            @click="changeState('archive')"
          >
            Archive
          </button>
        </div>
      </form>
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
.form-actions button, fieldset button, .form-alert button { min-height: 38px; padding: 8px 13px; border: 1px solid #aeb8b1; border-radius: 4px; background: #fff; cursor: pointer; }
.form-actions .primary-command { width: auto; margin: 0; border-color: #202622; background: #202622; color: #fff; }
@media (max-width: 680px) {
  .admin-page-heading { align-items: flex-start; flex-direction: column; }
  .form-grid { grid-template-columns: minmax(0, 1fr); }
  .wide { grid-column: auto; }
  .form-alert { align-items: flex-start; flex-direction: column; }
}
</style>
