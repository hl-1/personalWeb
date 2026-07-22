<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'
import { useAdminOperationFeedback, type AdminOperation } from '../../features/admin/admin-operation-feedback'
import { confirmAdminAction } from '../../features/admin/admin-confirmation'

const props = defineProps<{ adminClient: AdminClient }>()
const feedback = useAdminOperationFeedback()
const filters = reactive({ page: 0, size: 20, status: undefined as 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | undefined, query: '' })
const projects = useQuery(computed(() => ({
  queryKey: ['admin', 'projects', 'list', { ...filters }],
  queryFn: () => props.adminClient.listProjects({ page: filters.page, size: filters.size,
    ...(filters.status ? { status: filters.status } : {}), ...(filters.query.trim() ? { query: filters.query } : {}) }),
  retry: false,
})))
const pending = ref<string | undefined>()
const currentPage = computed({
  get: () => filters.page + 1,
  set: (page: number) => { filters.page = page - 1 },
})
const state = computed<'loading' | 'empty' | 'error' | 'unauthorized' | 'forbidden' | 'ready'>(() => {
  if (projects.isPending.value) return 'loading'
  const error = projects.error.value
  if (error instanceof AdminApiError && error.kind === 'unauthorized') return 'unauthorized'
  if (error instanceof AdminApiError && error.kind === 'forbidden') return 'forbidden'
  if (projects.isError.value) return 'error'
  return projects.data.value?.items.length === 0 ? 'empty' : 'ready'
})
async function run(id: string, operation: AdminOperation, action: () => Promise<unknown>) {
  if (pending.value) return
  pending.value = id
  try { await action(); feedback.succeeded(operation); await projects.refetch() }
  catch { feedback.failed(operation) }
  finally { pending.value = undefined }
}
async function remove(id: string, version: number) {
  const result = await confirmAdminAction({
    title: '删除项目',
    message: '确定删除这个草稿项目吗？',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
  if (result === 'confirmed') await run(id, 'project.delete', () => props.adminClient.deleteProject(id, version))
}
</script>

<template>
  <section>
    <header class="page-head">
      <div><p>Portfolio</p><h1>Projects</h1></div>
      <RouterLink
        v-slot="{ navigate }"
        custom
        to="/admin/portfolio/projects/new"
      >
        <el-button
          type="primary"
          @click="navigate"
        >
          New project
        </el-button>
      </RouterLink>
    </header>
    <el-form
      class="filters"
      inline
      @submit.prevent="projects.refetch()"
    >
      <el-form-item label="Status">
        <el-select
          v-model="filters.status"
          clearable
          placeholder="All"
          aria-label="Status"
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
          aria-label="Search"
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
      empty-label="No projects"
      @retry="projects.refetch()"
    >
      <el-table
        data-testid="project-list"
        :data="projects.data.value?.items ?? []"
        row-key="id"
      >
        <el-table-column
          label="Project"
          min-width="210"
        >
          <template #default="{ row: project }">
            <div class="identity">
              <RouterLink :to="`/admin/portfolio/projects/${project.id}`">
                {{ project.title }}
              </RouterLink>
              <span>{{ project.slug }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="Status"
          width="130"
        >
          <template #default="{ row: project }">
            <el-tag effect="plain">
              {{ project.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="summary"
          label="Summary"
          min-width="220"
        />
        <el-table-column
          label="Actions"
          min-width="190"
          fixed="right"
        >
          <template #default="{ row: project }">
            <div class="actions">
              <el-button
                v-if="project.status === 'DRAFT'"
                data-testid="publish-project"
                size="small"
                type="primary"
                :disabled="!!pending"
                @click="run(project.id, 'project.publish', () => adminClient.publishProject(project.id, project.version, null))"
              >
                Publish
              </el-button>
              <el-button
                v-if="project.status === 'PUBLISHED'"
                data-testid="archive-project"
                size="small"
                :disabled="!!pending"
                @click="run(project.id, 'project.archive', () => adminClient.archiveProject(project.id, project.version))"
              >
                Archive
              </el-button>
              <el-button
                v-if="project.status === 'DRAFT'"
                size="small"
                type="danger"
                plain
                :disabled="!!pending"
                @click="remove(project.id, project.version)"
              >
                Delete
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </AdminPageState>
    <el-pagination
      v-if="projects.data.value && projects.data.value.totalPages > 1"
      v-model:current-page="currentPage"
      class="pagination"
      :page-size="filters.size"
      :total="projects.data.value.totalElements"
      layout="prev, pager, next"
    />
  </section>
</template>

<style scoped>
.page-head { display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:22px }
.page-head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}
.page-head h1{margin:5px 0 0;font-size:30px;overflow-wrap:anywhere}
.filters{display:flex;align-items:end;gap:10px;margin-bottom:20px}
.filters :deep(.el-form-item){margin:0}
.filters :deep(.el-select){width:170px}
.search-field{flex:1;min-width:220px}
.identity{display:grid;gap:4px;min-width:0;overflow-wrap:anywhere}
.identity span{color:#68736c;font-size:12px}
.actions{display:flex;flex-wrap:wrap;gap:6px}
.actions :deep(.el-button + .el-button){margin-left:0}
.pagination{justify-content:flex-end;margin-top:20px}
@media(max-width:720px){.page-head{align-items:flex-start;flex-direction:column}.filters{align-items:stretch;flex-direction:column}.filters :deep(.el-form-item),.filters :deep(.el-select){width:100%}.search-field{width:100%;min-width:0}}
</style>
