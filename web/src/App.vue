<script setup lang="ts">
import { computed } from 'vue'
import { RouterView } from 'vue-router'
import type { AuthClient } from './features/auth/auth-client'
import { useAuthLogout, useAuthQuery } from './features/auth/auth-query'

const props = defineProps<{ authClient: AuthClient }>()
const authQuery = useAuthQuery(props.authClient)
const logout = useAuthLogout(props.authClient)
const currentUser = computed(() => !logout.isSuccess.value && authQuery.data.value?.authenticated
  ? authQuery.data.value.user
  : null)
const isAdmin = computed(() => currentUser.value?.roles.includes('ADMIN') ?? false)
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink
        class="brand"
        to="/"
      >
        <span class="brand-mark">SS</span>
        <span>StudyStack</span>
      </RouterLink>
      <nav
        class="primary-nav"
        aria-label="Primary navigation"
      >
        <RouterLink to="/about">
          About
        </RouterLink>
        <RouterLink to="/blog">
          Blog
        </RouterLink>
        <RouterLink to="/projects">
          Projects
        </RouterLink>
        <RouterLink
          v-if="isAdmin"
          data-testid="admin-link"
          to="/admin"
        >
          Admin
        </RouterLink>
        <span
          v-if="currentUser"
          class="account-name"
        >{{ currentUser.displayName }}</span>
        <button
          v-if="currentUser"
          class="text-command"
          data-testid="logout-button"
          type="button"
          :disabled="logout.isPending.value"
          @click="logout.mutate()"
        >
          Sign out
        </button>
        <RouterLink
          v-else
          data-testid="login-link"
          to="/login"
        >
          Sign in
        </RouterLink>
      </nav>
    </header>
    <main class="app-main">
      <RouterView />
    </main>
    <footer class="app-footer">
      <span>StudyStack</span>
      <RouterLink to="/foundation">
        Foundation
      </RouterLink>
    </footer>
  </div>
</template>

<style>
:root {
  --ink: #1e2421;
  --ink-soft: #3f4a44;
  --muted: #68736c;
  --surface: #ffffff;
  --surface-soft: #f4f6f4;
  --line: #dce1dd;
  --line-strong: #aeb8b1;
  --accent: #2f7758;
  --accent-dark: #185c40;
  --accent-soft: #e6f0e9;
  color: var(--ink);
  background: var(--surface-soft);
  font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  font-synthesis: none;
  letter-spacing: 0;
}

* {
  box-sizing: border-box;
}

body {
  margin: 0;
  min-width: 320px;
  min-height: 100vh;
  overflow-x: hidden;
}

button,
a {
  font: inherit;
}

a {
  color: var(--accent-dark);
}

.app-shell {
  min-height: 100vh;
}

.app-header {
  min-height: 66px;
  padding: 0 max(24px, calc((100vw - 1120px) / 2));
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 96%, transparent);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink);
  font-size: 18px;
  font-weight: 700;
  text-decoration: none;
}

.brand-mark {
  display: inline-grid;
  width: 30px;
  height: 30px;
  place-items: center;
  background: var(--ink);
  color: var(--surface);
  font-family: Georgia, "Times New Roman", serif;
  font-size: 12px;
}

.primary-nav {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  min-width: 0;
}

.primary-nav a,
.text-command {
  color: var(--ink-soft);
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.primary-nav a.router-link-active {
  color: var(--accent-dark);
}

.account-name {
  max-width: 180px;
  overflow: hidden;
  color: var(--muted);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.text-command {
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.text-command:disabled {
  cursor: wait;
  opacity: 0.55;
}

.app-main {
  width: min(100% - 48px, 1120px);
  margin: 0 auto;
  padding: 52px 0 80px;
}

.app-footer {
  display: flex;
  width: min(100% - 48px, 1120px);
  min-height: 78px;
  align-items: center;
  justify-content: space-between;
  margin: 0 auto;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}

.app-footer a {
  color: var(--muted);
}

.public-page,
.detail-page {
  width: min(100%, 880px);
  margin: 0 auto;
}

.detail-page {
  width: min(100%, 760px);
}

.page-heading,
.detail-heading {
  margin-bottom: 40px;
}

.page-heading h1,
.detail-heading h1 {
  margin: 0;
  font-family: Georgia, "Times New Roman", serif;
  font-size: 46px;
  font-weight: 600;
  line-height: 1.12;
  overflow-wrap: anywhere;
}

.page-heading > p:last-child,
.detail-summary {
  max-width: 700px;
  margin: 16px 0 0;
  color: var(--muted);
  font-size: 17px;
  line-height: 1.65;
}

.section-heading-row {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 24px;
}

.section-heading-row h2 {
  margin: 0;
  font-size: 27px;
}

.section-heading-row > a,
.back-link {
  font-size: 14px;
  font-weight: 750;
}

.back-link {
  display: inline-block;
  margin-bottom: 30px;
}

.content-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.content-list li {
  padding: 26px 0;
  border-top: 1px solid var(--line);
}

.content-list li:last-child {
  border-bottom: 1px solid var(--line);
}

.content-list h2 {
  margin: 8px 0;
  font-size: 24px;
  line-height: 1.3;
  overflow-wrap: anywhere;
}

.content-list h2 a {
  color: var(--ink);
  text-decoration: none;
}

.content-list h2 a:hover {
  color: var(--accent-dark);
}

.content-list p {
  max-width: 720px;
  margin: 0;
  color: var(--muted);
  line-height: 1.6;
}

.item-meta,
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: var(--muted);
  font-size: 13px;
}

.tag-row {
  margin-top: 14px;
  color: var(--accent-dark);
}

.page-section {
  max-width: 720px;
}

.page-section h1,
.auth-panel h1 {
  margin: 0 0 12px;
  font-size: 32px;
  line-height: 1.2;
}

.page-section p,
.auth-summary {
  color: #5e6861;
  line-height: 1.6;
}

.section-kicker,
.auth-kicker {
  margin: 0 0 8px;
  color: var(--accent-dark);
  font-size: 12px;
  font-weight: 700;
  text-transform: uppercase;
}

.auth-page {
  display: grid;
  min-height: calc(100vh - 180px);
  place-items: start center;
}

.auth-panel {
  width: min(100%, 420px);
  padding: 28px;
  border: 1px solid #d9dfda;
  border-radius: 6px;
  background: #ffffff;
}

.auth-summary {
  margin: 0 0 24px;
}

.auth-error {
  margin: 0 0 20px;
  padding: 12px 14px;
  border-left: 3px solid #b23a32;
  background: #fff4f2;
  color: #76261f;
  line-height: 1.45;
}

.primary-command,
.secondary-command {
  display: inline-flex;
  min-height: 42px;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
  font-weight: 700;
  text-decoration: none;
}

.primary-command {
  width: 100%;
  padding: 10px 18px;
  background: #202522;
  color: #ffffff;
}

.secondary-command {
  margin-top: 12px;
  padding: 9px 14px;
  border: 1px solid #aeb8b1;
  color: #35423a;
}

@media (max-width: 640px) {
  .app-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 12px;
    padding: 16px 20px;
  }

  .primary-nav {
    width: 100%;
    flex-wrap: wrap;
    justify-content: flex-start;
  }

  .app-main {
    width: min(100% - 32px, 1040px);
    padding-top: 32px;
  }

  .app-footer {
    width: min(100% - 32px, 1040px);
  }

  .page-heading h1,
  .detail-heading h1 {
    font-size: 36px;
  }

  .section-heading-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 8px;
  }

  .account-name {
    max-width: 120px;
  }
}
</style>
