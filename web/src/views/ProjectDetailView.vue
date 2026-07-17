<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { PortfolioApiError, type PortfolioClient } from '../features/portfolio/portfolio-client'
import { createProjectDetailQueryOptions } from '../features/portfolio/portfolio-query'
import { parseOptionalPublicSlug } from '../shared/api/slug-schema'
import SafeMarkdownView from '../shared/content/SafeMarkdownView.vue'
import { usePageSeo } from '../shared/seo/use-page-seo'
import StatusView from '../shared/ui/StatusView.vue'

const props = defineProps<{ portfolioClient: PortfolioClient, slug: string }>()
const project = useQuery(computed(() => createProjectDetailQueryOptions(
  props.portfolioClient,
  props.slug,
)))
const notFound = computed(() => parseOptionalPublicSlug(props.slug) === undefined
  || (project.error.value instanceof PortfolioApiError
    && project.error.value.kind === 'not_found'))

usePageSeo(computed(() => ({
  title: project.data.value?.title ?? 'Project',
  description: project.data.value?.summary,
  canonicalPath: project.data.value?.canonicalPath ?? `/projects/${props.slug}`,
})))
</script>

<template>
  <article
    class="detail-page"
    data-testid="project-detail-view"
  >
    <RouterLink
      class="back-link"
      to="/projects"
    >
      Back to Projects
    </RouterLink>
    <StatusView
      v-if="notFound"
      kind="not-found"
      title="Project not found"
      message="This project is unavailable or has not been published."
      heading-level="h1"
    />
    <StatusView
      v-else-if="project.isPending.value"
      kind="loading"
      title="Loading project"
      message="Fetching the published project."
      heading-level="h1"
    />
    <StatusView
      v-else-if="project.isError.value"
      kind="error"
      title="Project could not be loaded"
      message="The public portfolio service is temporarily unavailable."
      heading-level="h1"
    />
    <template v-else-if="project.data.value">
      <header class="detail-heading">
        <p class="section-kicker">
          Project
        </p>
        <h1>{{ project.data.value.title }}</h1>
        <p class="detail-summary">
          {{ project.data.value.summary }}
        </p>
        <div class="project-links">
          <a
            v-if="project.data.value.projectUrl"
            :href="project.data.value.projectUrl"
            rel="noopener noreferrer"
          >Visit project</a>
          <a
            v-if="project.data.value.repositoryUrl"
            :href="project.data.value.repositoryUrl"
            rel="noopener noreferrer"
          >View repository</a>
        </div>
      </header>
      <SafeMarkdownView :html="project.data.value.descriptionHtml" />
    </template>
  </article>
</template>

<style scoped>
.project-links {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 18px;
  margin-top: 22px;
}

.project-links a {
  overflow-wrap: anywhere;
  font-weight: 700;
}
</style>
