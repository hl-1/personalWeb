<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { RouterLink } from 'vue-router'
import type { ContentClient } from '../features/content/content-client'
import { createArticleListQueryOptions } from '../features/content/content-query'
import { PortfolioApiError, type PortfolioClient } from '../features/portfolio/portfolio-client'
import {
  createPortfolioProfileQueryOptions,
  createProjectListQueryOptions,
} from '../features/portfolio/portfolio-query'
import { usePageSeo } from '../shared/seo/use-page-seo'
import StatusView from '../shared/ui/StatusView.vue'

const props = defineProps<{
  contentClient: ContentClient
  portfolioClient: PortfolioClient
}>()
const profile = useQuery(createPortfolioProfileQueryOptions(props.portfolioClient))
const articles = useQuery(createArticleListQueryOptions(props.contentClient, { page: 0, size: 3 }))
const projects = useQuery(createProjectListQueryOptions(
  props.portfolioClient,
  { page: 0, size: 3, featured: true },
))
const profileMissing = () => profile.error.value instanceof PortfolioApiError
  && profile.error.value.kind === 'not_found'

usePageSeo({
  title: 'StudyStack',
  description: 'A public notebook and portfolio about Java, architecture, and dependable software delivery.',
  canonicalPath: '/',
})
</script>

<template>
  <div
    class="home-view"
    data-testid="home-view"
  >
    <section class="home-intro">
      <div>
        <p class="section-kicker">
          Java · Architecture · Delivery
        </p>
        <h1>StudyStack</h1>
        <p class="home-lede">
          A working notebook and portfolio for software built with care, tested at its boundaries,
          and explained clearly.
        </p>
        <div class="home-actions">
          <RouterLink
            class="primary-link"
            to="/blog"
          >
            Read the blog
          </RouterLink>
          <RouterLink
            class="secondary-link"
            to="/projects"
          >
            Browse projects
          </RouterLink>
        </div>
      </div>
      <div
        class="home-mark"
        aria-hidden="true"
      >
        <span>SS</span>
        <small>Build log / 2026</small>
      </div>
    </section>

    <section class="home-section">
      <div class="section-heading-row">
        <div>
          <p class="section-kicker">
            Profile
          </p>
          <h2>About the work</h2>
        </div>
        <RouterLink to="/about">
          Full profile
        </RouterLink>
      </div>
      <p
        v-if="profile.data.value"
        class="profile-summary"
      >
        <strong>{{ profile.data.value.displayName }}</strong>
        <span>{{ profile.data.value.headline }}</span>
      </p>
      <StatusView
        v-else-if="profileMissing()"
        kind="empty"
        data-testid="home-profile-empty"
        title="Public profile coming soon"
        message="StudyStack remains available while the profile is being prepared."
      />
      <StatusView
        v-else-if="profile.isError.value"
        kind="error"
        title="Profile could not be loaded"
        message="The rest of the public site remains available."
      />
      <StatusView
        v-else
        kind="loading"
        title="Loading profile"
        message="Fetching the public profile."
      />
    </section>

    <section class="home-section">
      <div class="section-heading-row">
        <div>
          <p class="section-kicker">
            Latest
          </p>
          <h2>Recent writing</h2>
        </div>
        <RouterLink to="/blog">
          All articles
        </RouterLink>
      </div>
      <StatusView
        v-if="articles.isPending.value"
        kind="loading"
        title="Loading recent writing"
        message="Fetching the latest published articles."
      />
      <StatusView
        v-else-if="articles.isError.value"
        kind="error"
        title="Recent writing could not be loaded"
        message="Visit the blog again shortly."
      />
      <StatusView
        v-else-if="!articles.data.value?.items.length"
        kind="empty"
        data-testid="home-articles-empty"
        title="No published articles yet"
        message="The first StudyStack notes will appear here."
      />
      <ul
        v-else
        class="home-list"
      >
        <li
          v-for="article in articles.data.value.items"
          :key="article.id"
        >
          <time :datetime="article.publishedAt">{{ new Date(article.publishedAt).toLocaleDateString() }}</time>
          <div>
            <h3>
              <RouterLink :to="`/blog/${article.slug}`">
                {{ article.title }}
              </RouterLink>
            </h3>
            <p>{{ article.summary }}</p>
          </div>
        </li>
      </ul>
    </section>

    <section class="home-section">
      <div class="section-heading-row">
        <div>
          <p class="section-kicker">
            Selected work
          </p>
          <h2>Featured projects</h2>
        </div>
        <RouterLink to="/projects">
          All projects
        </RouterLink>
      </div>
      <StatusView
        v-if="projects.isPending.value"
        kind="loading"
        title="Loading featured projects"
        message="Fetching selected project work."
      />
      <StatusView
        v-else-if="projects.isError.value"
        kind="error"
        title="Featured projects could not be loaded"
        message="Visit the project archive again shortly."
      />
      <StatusView
        v-else-if="!projects.data.value?.items.length"
        kind="empty"
        data-testid="home-projects-empty"
        title="No featured projects yet"
        message="Selected StudyStack work will appear here."
      />
      <ul
        v-else
        class="home-list"
      >
        <li
          v-for="project in projects.data.value.items"
          :key="project.id"
        >
          <span class="project-index">0{{ projects.data.value.items.indexOf(project) + 1 }}</span>
          <div>
            <h3>
              <RouterLink :to="`/projects/${project.slug}`">
                {{ project.title }}
              </RouterLink>
            </h3>
            <p>{{ project.summary }}</p>
          </div>
        </li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.home-intro {
  display: grid;
  min-height: 390px;
  align-items: center;
  grid-template-columns: minmax(0, 1.4fr) minmax(220px, 0.6fr);
  gap: 56px;
  padding: 42px 0 64px;
}

.home-intro h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 68px;
  font-weight: 600;
  line-height: 1;
}

.home-lede {
  max-width: 650px;
  margin: 24px 0 0;
  color: var(--ink-soft);
  font-size: 20px;
  line-height: 1.55;
}

.home-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 30px;
}

.primary-link,
.secondary-link {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  padding: 9px 16px;
  border-radius: 5px;
  font-weight: 750;
  text-decoration: none;
}

.primary-link {
  background: var(--ink);
  color: var(--surface);
}

.secondary-link {
  border: 1px solid var(--line-strong);
  color: var(--ink);
}

.home-mark {
  display: grid;
  aspect-ratio: 1;
  align-content: space-between;
  padding: 24px;
  border: 1px solid var(--line-strong);
  background: var(--accent-soft);
}

.home-mark span {
  font-family: Georgia, "Times New Roman", serif;
  font-size: 72px;
  line-height: 1;
}

.home-mark small {
  color: var(--accent-dark);
  font-weight: 750;
}

.home-section {
  padding: 48px 0;
  border-top: 1px solid var(--line);
}

.profile-summary {
  display: grid;
  margin: 0;
  gap: 6px;
}

.profile-summary strong {
  font-size: 22px;
}

.profile-summary span {
  color: var(--muted);
}

.home-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.home-list li {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  gap: 24px;
  padding: 20px 0;
  border-top: 1px solid var(--line);
}

.home-list time,
.project-index {
  color: var(--muted);
  font-size: 13px;
}

.home-list h3 {
  margin: 0 0 6px;
  font-size: 20px;
  overflow-wrap: anywhere;
}

.home-list h3 a {
  color: var(--ink);
  text-decoration: none;
}

.home-list p {
  margin: 0;
  color: var(--muted);
  line-height: 1.55;
}

@media (max-width: 720px) {
  .home-intro {
    min-height: auto;
    grid-template-columns: 1fr;
    gap: 36px;
    padding: 20px 0 48px;
  }

  .home-intro h1 {
    font-size: 48px;
  }

  .home-lede {
    font-size: 18px;
  }

  .home-mark {
    width: min(100%, 240px);
  }

  .home-list li {
    grid-template-columns: 1fr;
    gap: 8px;
  }
}
</style>
