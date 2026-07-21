<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { AdminApiError, type AdminClient } from '../../features/admin/admin-client'
import AdminPageState from '../../features/admin/components/AdminPageState.vue'

const props = defineProps<{ adminClient: AdminClient }>()
const filters = reactive({ page: 0, size: 20, status: undefined as 'DRAFT' | 'PUBLISHED' | 'ARCHIVED' | undefined, query: '' })
const projects = useQuery(computed(() => ({
  queryKey: ['admin', 'projects', 'list', { ...filters }],
  queryFn: () => props.adminClient.listProjects({ page: filters.page, size: filters.size,
    ...(filters.status ? { status: filters.status } : {}), ...(filters.query.trim() ? { query: filters.query } : {}) }),
  retry: false,
})))
const pending = ref<string | undefined>()
const state = computed<'loading' | 'empty' | 'error' | 'unauthorized' | 'forbidden' | 'ready'>(() => {
  if (projects.isPending.value) return 'loading'
  const error = projects.error.value
  if (error instanceof AdminApiError && error.kind === 'unauthorized') return 'unauthorized'
  if (error instanceof AdminApiError && error.kind === 'forbidden') return 'forbidden'
  if (projects.isError.value) return 'error'
  return projects.data.value?.items.length === 0 ? 'empty' : 'ready'
})
async function run(id: string, action: () => Promise<unknown>) {
  if (pending.value) return
  pending.value = id
  try { await action(); await projects.refetch() } finally { pending.value = undefined }
}
async function remove(id: string, version: number) {
  if (globalThis.confirm('Delete this draft project?')) await run(id, () => props.adminClient.deleteProject(id, version))
}
</script>

<template>
  <section>
    <header class="page-head">
      <div><p>Portfolio</p><h1>Projects</h1></div><RouterLink to="/admin/portfolio/projects/new">
        New project
      </RouterLink>
    </header>
    <form
      class="filters"
      @submit.prevent="projects.refetch()"
    >
      <select
        v-model="filters.status"
        aria-label="Status"
      >
        <option :value="undefined">
          All
        </option><option>DRAFT</option><option>PUBLISHED</option><option>ARCHIVED</option>
      </select>
      <input
        v-model="filters.query"
        type="search"
        maxlength="100"
        aria-label="Search"
      ><button type="submit">
        Search
      </button>
    </form>
    <AdminPageState
      :state="state"
      empty-label="No projects"
      @retry="projects.refetch()"
    >
      <div
        data-testid="project-list"
        class="list"
      >
        <article
          v-for="project in projects.data.value?.items"
          :key="project.id"
        >
          <div class="identity">
            <RouterLink :to="`/admin/portfolio/projects/${project.id}`">
              {{ project.title }}
            </RouterLink><span>{{ project.slug }}</span>
          </div>
          <span>{{ project.status }}</span><span>{{ project.summary }}</span>
          <div class="actions">
            <button
              v-if="project.status === 'DRAFT'"
              data-testid="publish-project"
              type="button"
              :disabled="!!pending"
              @click="run(project.id, () => adminClient.publishProject(project.id, project.version, null))"
            >
              Publish
            </button>
            <button
              v-if="project.status === 'PUBLISHED'"
              data-testid="archive-project"
              type="button"
              :disabled="!!pending"
              @click="run(project.id, () => adminClient.archiveProject(project.id, project.version))"
            >
              Archive
            </button>
            <button
              v-if="project.status === 'DRAFT'"
              type="button"
              :disabled="!!pending"
              @click="remove(project.id, project.version)"
            >
              Delete
            </button>
          </div>
        </article>
      </div>
    </AdminPageState>
  </section>
</template>

<style scoped>
.page-head { display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:22px }.page-head p{margin:0;color:#2f7758;font-size:12px;font-weight:750;text-transform:uppercase}.page-head h1{margin:5px 0 0;font-size:30px;overflow-wrap:anywhere}.page-head>a{padding:9px 13px;background:#202622;color:#fff;text-decoration:none;border-radius:4px}.filters{display:grid;grid-template-columns:150px minmax(180px,1fr) auto;gap:10px;margin-bottom:20px}.filters>*{min-height:38px;padding:7px 9px;border:1px solid #aeb8b1;border-radius:4px;background:#fff}.list{border-top:1px solid #d8dfda}.list article{display:grid;grid-template-columns:minmax(160px,1fr) 100px minmax(120px,1fr) auto;align-items:center;gap:12px;padding:14px 4px;border-bottom:1px solid #d8dfda;min-width:0}.identity,.list article>span{min-width:0;overflow-wrap:anywhere}.identity span{display:block;color:#68736c;font-size:12px;margin-top:4px}.actions{display:flex;flex-wrap:wrap;gap:6px}.actions button{min-height:34px;padding:6px 9px;border:1px solid #aeb8b1;background:#fff;border-radius:4px}@media(max-width:720px){.page-head{align-items:flex-start;flex-direction:column}.filters,.list article{grid-template-columns:minmax(0,1fr)}}
</style>
