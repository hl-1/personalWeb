<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { PortfolioClient } from '../features/portfolio/portfolio-client'
import { createProjectListQueryOptions } from '../features/portfolio/portfolio-query'
import { usePageSeo } from '../shared/seo/use-page-seo'
import PaginationNav from '../shared/ui/PaginationNav.vue'
import StatusView from '../shared/ui/StatusView.vue'

const props = defineProps<{ portfolioClient: PortfolioClient }>()
const route = useRoute()
const router = useRouter()
const page = computed(() => {
  const value = route.query.page
  if (typeof value !== 'string' || !/^\d+$/.test(value)) {
    return 0
  }
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : 0
})
const projects = useQuery(computed(() => createProjectListQueryOptions(
  props.portfolioClient,
  { page: page.value, size: 10 },
)))

usePageSeo({
  title: 'Projects',
  description: 'Selected software projects, experiments, and delivery work from StudyStack.',
  canonicalPath: '/projects',
})

function changePage(nextPage: number): void {
  void router.push({
    path: '/projects',
    query: nextPage === 0 ? {} : { page: String(nextPage) },
  })
}
</script>

<template>
  <section
    class="public-page"
    data-testid="project-list-view"
  >
    <header class="page-heading">
      <p class="section-kicker">
        Work
      </p>
      <h1>Projects</h1>
      <p>Selected systems, tools, and experiments focused on useful, maintainable software.</p>
    </header>
    <StatusView
      v-if="projects.isPending.value"
      kind="loading"
      title="Loading projects"
      message="Fetching published project work."
    />
    <StatusView
      v-else-if="projects.isError.value"
      kind="error"
      title="Projects could not be loaded"
      message="The public portfolio service is temporarily unavailable."
    />
    <StatusView
      v-else-if="!projects.data.value?.items.length"
      kind="empty"
      title="No published projects"
      message="Published project work will appear here when it is available."
    />
    <template v-else>
      <ul class="content-list project-list">
        <li
          v-for="project in projects.data.value.items"
          :key="project.id"
        >
          <div class="item-meta">
            <span v-if="project.featured">Featured</span>
            <time :datetime="project.publishedAt">{{ new Date(project.publishedAt).toLocaleDateString() }}</time>
          </div>
          <h2>
            <RouterLink :to="`/projects/${project.slug}`">
              {{ project.title }}
            </RouterLink>
          </h2>
          <p>{{ project.summary }}</p>
        </li>
      </ul>
      <PaginationNav
        :page="projects.data.value.page"
        :total-pages="projects.data.value.totalPages"
        @change="changePage"
      />
    </template>
  </section>
</template>
