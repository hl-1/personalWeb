<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import { createArticleListQueryOptions } from '../../features/admin/admin-query'
import type { AdminStatus } from '../../features/admin/admin-schema'
import { useAdminOperationFeedback, type AdminOperation } from '../../features/admin/admin-operation-feedback'
import { confirmAdminAction } from '../../features/admin/admin-confirmation'

const props = defineProps<{ adminClient: AdminClient }>()
const feedback = useAdminOperationFeedback()
const updatedAtFormatter = new Intl.DateTimeFormat(undefined, {
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
})
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
const currentPage = computed({
  get: () => filters.page + 1,
  set: (page: number) => { filters.page = page - 1 },
})

const state = computed<'loading' | 'empty' | 'error' | 'unauthorized' | 'forbidden' | 'ready'>(() => {
  if (articles.isPending.value) return 'loading'
  const error = articles.error.value
  if (error instanceof AdminApiError && error.kind === 'unauthorized') return 'unauthorized'
  if (error instanceof AdminApiError && error.kind === 'forbidden') return 'forbidden'
  if (articles.isError.value) return 'error'
  return articles.data.value?.items.length === 0 ? 'empty' : 'ready'
})

async function run(id: string, operation: AdminOperation, action: () => Promise<unknown>) {
  if (pendingId.value !== undefined) return
  pendingId.value = id
  try {
    await action()
    feedback.succeeded(operation)
    await articles.refetch()
  } catch {
    feedback.failed(operation)
  } finally {
    pendingId.value = undefined
  }
}

async function remove(id: string, version: number) {
  const result = await confirmAdminAction({
    title: '删除文章',
    message: '确定删除这篇草稿文章吗？',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  if (result !== 'confirmed') return
  await run(id, 'article.delete', () => props.adminClient.deleteArticle(id, version))
}

function applyFilters() {
  filters.page = 0
  void articles.refetch()
}

function formatUpdatedAt(value: string): string {
  return updatedAtFormatter.format(new Date(value))
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
        v-slot="{ navigate }"
        custom
        to="/admin/articles/new"
      >
        <el-button
          type="primary"
          @click="navigate"
        >
          New article
        </el-button>
      </RouterLink>
    </header>

    <el-form
      class="article-filters"
      inline
      @submit.prevent="applyFilters"
    >
      <el-form-item label="Status">
        <el-select
          v-model="filters.status"
          clearable
          placeholder="All"
        >
          <el-option
            label="Draft"
            value="DRAFT"
          />
          <el-option
            label="Published"
            value="PUBLISHED"
          />
          <el-option
            label="Archived"
            value="ARCHIVED"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        class="search-field"
        label="Search"
      >
        <el-input
          v-model="filters.query"
          maxlength="100"
          clearable
        />
      </el-form-item>
      <el-form-item>
        <el-button
          type="primary"
          native-type="submit"
        >
          Search
        </el-button>
      </el-form-item>
    </el-form>

    <AdminPageState
      :state="state"
      empty-label="No articles"
      @retry="articles.refetch()"
    >
      <el-table
        data-testid="article-list"
        :data="articles.data.value?.items ?? []"
        row-key="id"
      >
        <el-table-column
          label="Article"
          min-width="220"
        >
          <template #default="{ row: article }">
            <div class="article-identity">
              <RouterLink :to="`/admin/articles/${article.id}`">
                {{ article.title }}
              </RouterLink>
              <small>{{ article.slug }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="Status"
          width="130"
        >
          <template #default="{ row: article }">
            <el-tag effect="plain">
              {{ article.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="Updated"
          min-width="180"
        >
          <template #default="{ row: article }">
            <time
              class="updated-at"
              data-testid="article-updated-at"
              :datetime="article.updatedAt"
              :title="article.updatedAt"
            >{{ formatUpdatedAt(article.updatedAt) }}</time>
          </template>
        </el-table-column>
        <el-table-column
          label="Actions"
          min-width="190"
          fixed="right"
        >
          <template #default="{ row: article }">
            <div class="row-actions">
              <el-button
                v-if="article.status === 'DRAFT'"
                data-testid="publish-article"
                size="small"
                type="primary"
                :disabled="pendingId !== undefined"
                @click="run(article.id, 'article.publish', () => adminClient.publishArticle(article.id, article.version, null))"
              >
                Publish
              </el-button>
              <el-button
                v-if="article.status === 'PUBLISHED'"
                data-testid="archive-article"
                size="small"
                :disabled="pendingId !== undefined"
                @click="run(article.id, 'article.archive', () => adminClient.archiveArticle(article.id, article.version))"
              >
                Archive
              </el-button>
              <el-button
                v-if="article.status === 'DRAFT'"
                size="small"
                type="danger"
                plain
                :disabled="pendingId !== undefined"
                @click="remove(article.id, article.version)"
              >
                Delete
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </AdminPageState>

    <el-pagination
      v-if="articles.data.value && articles.data.value.totalPages > 1"
      v-model:current-page="currentPage"
      class="pagination"
      :page-size="filters.size"
      :total="articles.data.value.totalElements"
      layout="prev, pager, next"
    />
  </section>
</template>

<style scoped>
.admin-page-heading { display: flex; align-items: end; justify-content: space-between; gap: 20px; margin-bottom: 24px; }
.admin-page-heading p { margin: 0 0 5px; color: #2f7758; font-size: 12px; font-weight: 750; text-transform: uppercase; }
.admin-page-heading h1 { margin: 0; font-size: 30px; overflow-wrap: anywhere; }
.article-filters { display: flex; align-items: end; gap: 12px; margin-bottom: 22px; }
.article-filters :deep(.el-form-item) { margin: 0; }
.article-filters :deep(.el-select) { width: 170px; }
.search-field { flex: 1; min-width: 220px; }
.article-identity { display: grid; gap: 4px; }
.article-identity small { color: #6e7871; }
.updated-at { white-space: nowrap; }
.row-actions { display: flex; flex-wrap: wrap; gap: 6px; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
.pagination { justify-content: flex-end; margin-top: 20px; }
@media (max-width: 640px) {
  .admin-page-heading { align-items: flex-start; flex-direction: column; }
  .article-filters { align-items: stretch; flex-direction: column; }
  .article-filters :deep(.el-form-item), .article-filters :deep(.el-select) { width: 100%; }
  .search-field { width: 100%; min-width: 0; }
}
</style>
