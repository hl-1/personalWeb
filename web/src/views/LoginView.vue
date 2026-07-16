<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const errorMessages: Readonly<Record<string, string>> = {
  oauth_denied: 'GitHub authorization was cancelled.',
  invalid_profile: 'The GitHub profile could not be used.',
  identity_conflict: 'This GitHub identity is already linked.',
  account_disabled: 'This account has been disabled.',
  login_failed: 'Sign-in could not be completed.',
}

const route = useRoute()
const errorMessage = computed(() => {
  const code = typeof route.query.error === 'string' ? route.query.error : ''
  if (!code) {
    return null
  }
  return errorMessages[code] ?? errorMessages.login_failed
})
</script>

<template>
  <section
    class="auth-page"
    data-testid="login-view"
  >
    <div class="auth-panel">
      <p class="auth-kicker">
        StudyStack account
      </p>
      <h1>Sign in</h1>
      <p class="auth-summary">
        Use your GitHub account to continue.
      </p>

      <p
        v-if="errorMessage"
        class="auth-error"
        role="alert"
      >
        {{ errorMessage }}
      </p>

      <a
        class="primary-command"
        data-testid="github-login"
        href="/oauth2/authorization/github"
      >
        Continue with GitHub
      </a>
    </div>
  </section>
</template>
