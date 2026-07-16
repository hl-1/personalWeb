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
        StudyStack
      </RouterLink>
      <nav
        class="primary-nav"
        aria-label="Primary navigation"
      >
        <RouterLink to="/foundation">
          Foundation
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
  </div>
</template>

<style>
:root {
  color: #202522;
  background: #f5f7f5;
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
}

button,
a {
  font: inherit;
}

a {
  color: #1f5f46;
}

.app-shell {
  min-height: 100vh;
}

.app-header {
  min-height: 58px;
  padding: 0 28px;
  border-bottom: 1px solid #d9dfda;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
}

.brand {
  color: #202522;
  font-size: 18px;
  font-weight: 700;
  text-decoration: none;
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
  color: #35423a;
  font-size: 14px;
  font-weight: 600;
  text-decoration: none;
}

.primary-nav a.router-link-active {
  color: #13704b;
}

.account-name {
  max-width: 180px;
  overflow: hidden;
  color: #667069;
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
  width: min(100% - 40px, 1040px);
  margin: 0 auto;
  padding: 48px 0 72px;
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
  color: #13704b;
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

  .account-name {
    max-width: 120px;
  }
}
</style>
