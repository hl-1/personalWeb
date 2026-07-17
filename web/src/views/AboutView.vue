<script setup lang="ts">
import { useQuery } from '@tanstack/vue-query'
import { computed } from 'vue'
import { PortfolioApiError, type PortfolioClient } from '../features/portfolio/portfolio-client'
import {
  createExperienceListQueryOptions,
  createPortfolioProfileQueryOptions,
  createSkillListQueryOptions,
} from '../features/portfolio/portfolio-query'
import SafeMarkdownView from '../shared/content/SafeMarkdownView.vue'
import { usePageSeo } from '../shared/seo/use-page-seo'
import StatusView from '../shared/ui/StatusView.vue'

const props = defineProps<{ portfolioClient: PortfolioClient }>()
const profile = useQuery(createPortfolioProfileQueryOptions(props.portfolioClient))
const skills = useQuery(createSkillListQueryOptions(props.portfolioClient))
const experiences = useQuery(createExperienceListQueryOptions(props.portfolioClient))
const profileMissing = computed(() => profile.error.value instanceof PortfolioApiError
  && profile.error.value.kind === 'not_found')

usePageSeo(computed(() => ({
  title: 'About',
  description: profile.data.value?.seoDescription
    ?? 'Background, skills, and experience behind StudyStack.',
  canonicalPath: '/about',
})))
</script>

<template>
  <section
    class="public-page about-page"
    data-testid="about-view"
  >
    <header class="page-heading">
      <p class="section-kicker">
        Profile
      </p>
      <h1>About</h1>
    </header>

    <StatusView
      v-if="profile.isPending.value"
      kind="loading"
      title="Loading profile"
      message="Fetching the public profile."
    />
    <StatusView
      v-else-if="profileMissing"
      kind="empty"
      data-testid="about-profile-empty"
      title="Public profile coming soon"
      message="The public profile is being prepared."
    />
    <StatusView
      v-else-if="profile.isError.value"
      kind="error"
      title="Profile could not be loaded"
      message="The public profile is not available right now."
    />
    <section
      v-else-if="profile.data.value"
      class="profile-intro"
    >
      <h2>{{ profile.data.value.displayName }}</h2>
      <p class="profile-headline">
        {{ profile.data.value.headline }}
      </p>
      <SafeMarkdownView :html="profile.data.value.bioHtml" />
    </section>

    <section class="about-section">
      <div class="section-heading-row">
        <div>
          <p class="section-kicker">
            Capabilities
          </p>
          <h2>Skills</h2>
        </div>
      </div>
      <StatusView
        v-if="skills.isPending.value"
        kind="loading"
        title="Loading skills"
        message="Fetching visible skills."
      />
      <StatusView
        v-else-if="skills.isError.value"
        kind="error"
        title="Skills could not be loaded"
        message="Visible skills are temporarily unavailable."
      />
      <StatusView
        v-else-if="!skills.data.value?.length"
        kind="empty"
        title="No visible skills"
        message="Skills will appear here when they are published."
      />
      <dl
        v-else
        class="skill-list"
      >
        <div
          v-for="skill in skills.data.value"
          :key="skill.id"
        >
          <dt>{{ skill.name }}</dt>
          <dd>
            <span>{{ skill.category }}</span>
            <p v-if="skill.summary">
              {{ skill.summary }}
            </p>
          </dd>
        </div>
      </dl>
    </section>

    <section class="about-section">
      <div class="section-heading-row">
        <div>
          <p class="section-kicker">
            Timeline
          </p>
          <h2>Experience</h2>
        </div>
      </div>
      <StatusView
        v-if="experiences.isPending.value"
        kind="loading"
        title="Loading experience"
        message="Fetching visible experience."
      />
      <StatusView
        v-else-if="experiences.isError.value"
        kind="error"
        title="Experience could not be loaded"
        message="Visible experience is temporarily unavailable."
      />
      <StatusView
        v-else-if="!experiences.data.value?.length"
        kind="empty"
        title="No visible experience"
        message="Experience will appear here when it is published."
      />
      <ol
        v-else
        class="experience-list"
      >
        <li
          v-for="experience in experiences.data.value"
          :key="experience.id"
        >
          <div class="experience-period">
            <time :datetime="experience.startDate">{{ experience.startDate }}</time>
            <span>to</span>
            <time
              v-if="experience.endDate"
              :datetime="experience.endDate"
            >{{ experience.endDate }}</time>
            <span v-else>Present</span>
          </div>
          <h3>{{ experience.role }}</h3>
          <p class="experience-organization">
            {{ experience.organization }}
          </p>
          <SafeMarkdownView :html="experience.summaryHtml" />
        </li>
      </ol>
    </section>
  </section>
</template>

<style scoped>
.profile-intro {
  max-width: 760px;
  padding-bottom: 44px;
}

.profile-intro h2 {
  margin: 0 0 6px;
  font-size: 28px;
}

.profile-headline {
  margin: 0 0 28px;
  color: var(--accent-dark);
  font-size: 18px;
  font-weight: 650;
}

.about-section {
  padding: 44px 0;
  border-top: 1px solid var(--line);
}

.skill-list {
  margin: 0;
}

.skill-list > div {
  display: grid;
  grid-template-columns: minmax(140px, 0.35fr) minmax(0, 1fr);
  gap: 24px;
  padding: 16px 0;
  border-top: 1px solid var(--line);
}

.skill-list dt {
  font-weight: 750;
}

.skill-list dd {
  margin: 0;
  color: var(--muted);
}

.skill-list p {
  margin: 5px 0 0;
}

.experience-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.experience-list li {
  padding: 24px 0;
  border-top: 1px solid var(--line);
}

.experience-period {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  color: var(--muted);
  font-size: 13px;
}

.experience-list h3 {
  margin: 8px 0 3px;
  font-size: 20px;
}

.experience-organization {
  margin: 0 0 16px;
  color: var(--accent-dark);
  font-weight: 650;
}

@media (max-width: 600px) {
  .skill-list > div {
    grid-template-columns: 1fr;
    gap: 6px;
  }
}
</style>
