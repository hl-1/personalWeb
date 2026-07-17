<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import { ContentApiError, type ContentClient } from '../features/content/content-client'
import { createArticleDetailQueryOptions } from '../features/content/content-query'
import { parseOptionalPublicSlug } from '../shared/api/slug-schema'
import SafeMarkdownView from '../shared/content/SafeMarkdownView.vue'
import { usePageSeo } from '../shared/seo/use-page-seo'
import StatusView from '../shared/ui/StatusView.vue'

const props = defineProps<{ contentClient: ContentClient, slug: string }>()
const article = useQuery(computed(() => createArticleDetailQueryOptions(
  props.contentClient,
  props.slug,
)))
const notFound = computed(() => parseOptionalPublicSlug(props.slug) === undefined
  || (article.error.value instanceof ContentApiError
    && article.error.value.kind === 'not_found'))

usePageSeo(computed(() => ({
  title: article.data.value?.seoTitle ?? article.data.value?.title ?? 'Article',
  description: article.data.value?.seoDescription ?? article.data.value?.summary,
  canonicalPath: article.data.value?.canonicalPath ?? `/blog/${props.slug}`,
})))
</script>

<template>
  <article
    class="detail-page"
    data-testid="article-detail-view"
  >
    <RouterLink
      class="back-link"
      to="/blog"
    >
      Back to Blog
    </RouterLink>
    <StatusView
      v-if="notFound"
      kind="not-found"
      title="Article not found"
      message="This article is unavailable or has not been published."
      heading-level="h1"
    />
    <StatusView
      v-else-if="article.isPending.value"
      kind="loading"
      title="Loading article"
      message="Fetching the published article."
      heading-level="h1"
    />
    <StatusView
      v-else-if="article.isError.value"
      kind="error"
      title="Article could not be loaded"
      message="The public article service is temporarily unavailable."
      heading-level="h1"
    />
    <template v-else-if="article.data.value">
      <header class="detail-heading">
        <p class="section-kicker">
          Article
        </p>
        <h1>{{ article.data.value.title }}</h1>
        <p class="detail-summary">
          {{ article.data.value.summary }}
        </p>
        <div class="item-meta">
          <time :datetime="article.data.value.publishedAt">
            {{ new Date(article.data.value.publishedAt).toLocaleDateString() }}
          </time>
          <RouterLink
            v-if="article.data.value.category"
            :to="`/blog?category=${article.data.value.category}`"
          >
            {{ article.data.value.category }}
          </RouterLink>
          <RouterLink
            v-for="tag in article.data.value.tags"
            :key="tag"
            :to="`/blog?tag=${tag}`"
          >
            #{{ tag }}
          </RouterLink>
        </div>
      </header>
      <SafeMarkdownView :html="article.data.value.contentHtml" />
    </template>
  </article>
</template>
