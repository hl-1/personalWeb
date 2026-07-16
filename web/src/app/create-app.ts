import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, type Pinia } from 'pinia'
import { createApp, type App as VueApp } from 'vue'
import type { Router, RouterHistory } from 'vue-router'
import App from '../App.vue'
import { loadPublicEnv, type PublicEnv } from '../config/env'
import { createAuthClient, type AuthClient } from '../features/auth/auth-client'
import type { ReturnToStorage } from '../features/auth/return-to'
import { createAuthRouteAccess, createStudyStackRouter } from '../router'

export interface StudyStackAppOptions {
  history?: RouterHistory
  publicEnv?: Record<string, unknown>
  queryClient?: QueryClient
  authClient?: AuthClient
  returnToStorage?: ReturnToStorage
}

export interface StudyStackApplication {
  app: VueApp
  router: Router
  queryClient: QueryClient
  pinia: Pinia
  publicEnv: PublicEnv
  authClient: AuthClient
}

export function createStudyStackApp(options: StudyStackAppOptions = {}): StudyStackApplication {
  const publicEnv = loadPublicEnv(options.publicEnv)
  const queryClient = options.queryClient ?? new QueryClient()
  const authClient = options.authClient ?? createAuthClient({
    apiBaseUrl: publicEnv.VITE_API_BASE_URL,
  })
  const returnToStorage = options.returnToStorage ?? globalThis.sessionStorage
  const router = createStudyStackRouter(
    options.history,
    createAuthRouteAccess(authClient, queryClient, returnToStorage),
  )
  const app = createApp(App, { authClient })
  const pinia = createPinia()

  app.use(router)
  app.use(VueQueryPlugin, { queryClient })
  app.use(pinia)

  return { app, router, queryClient, pinia, publicEnv, authClient }
}
