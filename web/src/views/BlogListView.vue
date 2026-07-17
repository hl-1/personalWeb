<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { ContentClient } from '../features/content/content-client'
import {
  createArticleListQueryOptions,
  createCategoryListQueryOptions,
  createTagListQueryOptions,
} from '../features/content/content-query'
import { parseOptionalPublicSlug } from '../shared/api/slug-schema'
import PaginationNav from '../shared/ui/PaginationNav.vue'
import StatusView from '../shared/ui/StatusView.vue'
import { usePageSeo } from '../shared/seo/use-page-seo'

const props = defineProps<{ contentClient: ContentClient }>()
const route = useRoute()
const router = useRouter()
const params = computed(() => ({
  page: queryPage(route.query.page),
  size: 10,
  ...(querySlug(route.query.category) === undefined
    ? {}
    : { category: querySlug(route.query.category) }),
  ...(querySlug(route.query.tag) === undefined
    ? {}
    : { tag: querySlug(route.query.tag) }),
}))
const articles = useQuery(computed(() => createArticleListQueryOptions(
  props.contentClient,
  params.value,
)))
const categories = useQuery(createCategoryListQueryOptions(props.contentClient))
const tags = useQuery(createTagListQueryOptions(props.contentClient))

usePageSeo({
  title: 'Blog',
  description: 'Published notes on Java, backend architecture, and reliable software delivery.',
  canonicalPath: '/blog',
})

function queryPage(value: unknown): number {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) {
    return 0
  }
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) ? parsed : 0
}

function querySlug(value: unknown): string | undefined {
  return parseOptionalPublicSlug(value)
}

function filterLocation(kind: 'category' | 'tag', value: string) {
  return {
    path: '/blog',
    query: {
      ...(kind === 'category' ? { category: value } : {}),
      ...(kind === 'tag' ? { tag: value } : {}),
    },
  }
}

function changePage(page: number): void {
  void router.push({
    path: '/blog',
    query: {
      ...(params.value.category ? { category: params.value.category } : {}),
      ...(params.value.tag ? { tag: params.value.tag } : {}),
      ...(page === 0 ? {} : { page: String(page) }),
    },
  })
}
</script>

<template>
  <section
    class="public-page"
    data-testid="blog-list-view"
  >
    <header class="page-heading">
      <p class="section-kicker">
        Writing
      </p>
      <h1>Blog</h1>
      <p>Notes from building Java systems, studying architecture, and shipping dependable software.</p>
    </header>

    <div
      v-if="categories.data.value?.length || tags.data.value?.length"
      class="filter-bar"
      aria-label="Article filters"
    >
      <div v-if="categories.data.value?.length">
        <span>Categories</span>
        <RouterLink
          v-for="category in categories.data.value"
          :key="category.slug"
          :data-testid="`category-filter-${category.slug}`"
          :to="filterLocation('category', category.slug)"
        >
          {{ category.name }} <small>{{ category.publishedArticleCount }}</small>
        </RouterLink>
      </div>
      <div v-if="tags.data.value?.length">
        <span>Tags</span>
        <RouterLink
          v-for="tag in tags.data.value"
          :key="tag.slug"
          :data-testid="`tag-filter-${tag.slug}`"
          :to="filterLocation('tag', tag.slug)"
        >
          {{ tag.name }} <small>{{ tag.publishedArticleCount }}</small>
        </RouterLink>
      </div>
      <RouterLink
        v-if="params.category || params.tag"
        class="clear-filter"
        to="/blog"
      >
        Clear filters
      </RouterLink>
    </div>

    <StatusView
      v-if="articles.isPending.value"
      kind="loading"
      title="Loading articles"
      message="Fetching the latest published writing."
    />
    <StatusView
      v-else-if="articles.isError.value"
      kind="error"
      title="Articles could not be loaded"
      message="The public article service is temporarily unavailable."
    />
    <StatusView
      v-else-if="!articles.data.value?.items.length"
      kind="empty"
      title="No published articles"
      message="Published writing will appear here when it is available."
    />
    <template v-else>
      <ul class="content-list">
        <li
          v-for="article in articles.data.value.items"
          :key="article.id"
        >
          <div class="item-meta">
            <time :datetime="article.publishedAt">{{ new Date(article.publishedAt).toLocaleDateString() }}</time>
            <span v-if="article.category">{{ article.category }}</span>
          </div>
          <h2>
            <RouterLink :to="`/blog/${article.slug}`">
              {{ article.title }}
            </RouterLink>
          </h2>
          <p>{{ article.summary }}</p>
          <div
            v-if="article.tags.length"
            class="tag-row"
          >
            <span
              v-for="tag in article.tags"
              :key="tag"
            >{{ tag }}</span>
          </div>
        </li>
      </ul>
      <PaginationNav
        :page="articles.data.value.page"
        :total-pages="articles.data.value.totalPages"
        @change="changePage"
      />
    </template>
  </section>
</template>

<style scoped>
.filter-bar {
  display: grid;
  gap: 12px;
  margin-bottom: 34px;
  padding: 18px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}

.filter-bar > div {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-bar span:first-child {
  min-width: 86px;
  color: var(--muted);
  font-size: 13px;
  font-weight: 700;
}

.filter-bar a {
  padding: 5px 8px;
  border-radius: 4px;
  color: var(--ink-soft);
  font-size: 13px;
  text-decoration: none;
}

.filter-bar a.router-link-active,
.filter-bar a:hover {
  background: var(--accent-soft);
  color: var(--accent-dark);
}

.filter-bar small {
  color: var(--muted);
}

.clear-filter {
  justify-self: start;
}
</style>
