<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { createArticleListQueryOptions } from '../../features/admin/admin-query'
import type { AdminStatus } from '../../features/admin/admin-schema'

const props = defineProps<{ adminClient: AdminClient }>()
const filters = reactive<{ page: number; size: number; status?: AdminStatus; query: string }>({
  page: 0,
  size: 20,
  query: '',
})
const queryOptions = computed(() => createArticleListQueryOptions(props.adminClient, {
  page: filters.page,
  size: filters.size,
  ...(filters.status === undefined ? {} : { status: filters.status }),
  ...(filters.query.trim() === '' ? {} : { query: filters.query }),
}))
const articles = useQuery(queryOptions)
const pendingId = ref<string | undefined>()

const state = computed<'loading' | 'empty' | 'error' | 'unauthorized' | 'forbidden' | 'ready'>(() => {
  if (articles.isPending.value) return 'loading'
  const error = articles.error.value
  if (error instanceof AdminApiError && error.kind === 'unauthorized') return 'unauthorized'
  if (error instanceof AdminApiError && error.kind === 'forbidden') return 'forbidden'
  if (articles.isError.value) return 'error'
  return articles.data.value?.items.length === 0 ? 'empty' : 'ready'
})

async function run(id: string, action: () => Promise<unknown>) {
  if (pendingId.value !== undefined) return
  pendingId.value = id
  try {
    await action()
    await articles.refetch()
  } finally {
    pendingId.value = undefined
  }
}

async function remove(id: string, version: number) {
  if (globalThis.confirm && !globalThis.confirm('Delete this draft article?')) return
  await run(id, () => props.adminClient.deleteArticle(id, version))
}

function applyFilters() {
  filters.page = 0
  void articles.refetch()
}
</script>

<template>
  <section>
    <header class="admin-page-heading">
      <div>
        <p>Content</p>
        <h1>Articles</h1>
      </div>
      <RouterLink
        class="primary-action"
        to="/admin/articles/new"
      >
        New article
      </RouterLink>
    </header>

    <form
      class="article-filters"
      @submit.prevent="applyFilters"
    >
      <label>
        <span>Status</span>
        <select v-model="filters.status">
          <option :value="undefined">All</option>
          <option value="DRAFT">Draft</option>
          <option value="PUBLISHED">Published</option>
          <option value="ARCHIVED">Archived</option>
        </select>
      </label>
      <label class="search-field">
        <span>Search</span>
        <input
          v-model="filters.query"
          type="search"
          maxlength="100"
        >
      </label>
      <button type="submit">
        Search
      </button>
    </form>

    <AdminPageState
      :state="state"
      empty-label="No articles"
      @retry="articles.refetch()"
    >
      <div
        data-testid="article-list"
        class="article-table-wrap"
      >
        <table>
          <thead>
            <tr><th>Article</th><th>Status</th><th>Updated</th><th>Actions</th></tr>
          </thead>
          <tbody>
            <tr
              v-for="article in articles.data.value?.items"
              :key="article.id"
            >
              <td>
                <RouterLink :to="`/admin/articles/${article.id}`">
                  {{ article.title }}
                </RouterLink>
                <small>{{ article.slug }}</small>
              </td>
              <td><span class="status-label">{{ article.status }}</span></td>
              <td>{{ article.updatedAt }}</td>
              <td>
                <div class="row-actions">
                  <button
                    v-if="article.status === 'DRAFT'"
                    data-testid="publish-article"
                    type="button"
                    :disabled="pendingId !== undefined"
                    @click="run(article.id, () => adminClient.publishArticle(article.id, article.version, null))"
                  >
                    Publish
                  </button>
                  <button
                    v-if="article.status === 'PUBLISHED'"
                    data-testid="archive-article"
                    type="button"
                    :disabled="pendingId !== undefined"
                    @click="run(article.id, () => adminClient.archiveArticle(article.id, article.version))"
                  >
                    Archive
                  </button>
                  <button
                    v-if="article.status === 'DRAFT'"
                    type="button"
                    :disabled="pendingId !== undefined"
                    @click="remove(article.id, article.version)"
                  >
                    Delete
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </AdminPageState>

    <div
      v-if="articles.data.value && articles.data.value.totalPages > 1"
      class="pagination"
    >
      <button
        type="button"
        :disabled="filters.page === 0"
        @click="filters.page -= 1"
      >
        Previous
      </button>
      <span>Page {{ filters.page + 1 }} of {{ articles.data.value.totalPages }}</span>
      <button
        type="button"
        :disabled="filters.page + 1 >= articles.data.value.totalPages"
        @click="filters.page += 1"
      >
        Next
      </button>
    </div>
  </section>
</template>

<style scoped>
.admin-page-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
.admin-page-heading p { margin: 0 0 5px; color: #2f7758; font-size: 12px; font-weight: 750; text-transform: uppercase; }
.admin-page-heading h1 { margin: 0; font-size: 30px; overflow-wrap: anywhere; }
.primary-action { min-height: 38px; padding: 9px 14px; border-radius: 4px; background: #202622; color: #fff; font-weight: 700; text-decoration: none; }
.article-filters { display: grid; grid-template-columns: 150px minmax(180px, 1fr) auto; align-items: end; gap: 12px; margin-bottom: 22px; }
label { display: grid; gap: 5px; min-width: 0; color: #59645d; font-size: 12px; font-weight: 700; }
input, select, .article-filters button { width: 100%; min-height: 38px; padding: 7px 9px; border: 1px solid #aeb8b1; border-radius: 4px; background: #fff; }
.article-filters button { width: auto; cursor: pointer; }
.article-table-wrap { max-width: 100%; overflow-x: auto; }
table { width: 100%; min-width: 720px; border-collapse: collapse; }
th, td { padding: 13px 10px; border-bottom: 1px solid #d8dfda; text-align: left; vertical-align: top; }
th { color: #59645d; font-size: 12px; text-transform: uppercase; }
td small { display: block; margin-top: 4px; color: #6e7871; }
.status-label { font-size: 12px; font-weight: 750; }
.row-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.row-actions button, .pagination button { min-height: 32px; padding: 5px 9px; border: 1px solid #aeb8b1; border-radius: 4px; background: #fff; cursor: pointer; }
.pagination { display: flex; align-items: center; justify-content: flex-end; gap: 12px; margin-top: 20px; font-size: 13px; }
@media (max-width: 640px) {
  .admin-page-heading { align-items: flex-start; flex-direction: column; }
  .article-filters { grid-template-columns: minmax(0, 1fr); }
  .article-filters button { width: 100%; }
}
</style>
