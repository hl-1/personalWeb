import { QueryClient, VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia, type Pinia } from 'pinia'
import { createApp, type App as VueApp } from 'vue'
import type { Router, RouterHistory } from 'vue-router'
import App from '../App.vue'
import { loadPublicEnv, type PublicEnv } from '../config/env'
import { createStudyStackRouter } from '../router'

export interface StudyStackAppOptions {
  history?: RouterHistory
  publicEnv?: Record<string, unknown>
  queryClient?: QueryClient
}

export interface StudyStackApplication {
  app: VueApp
  router: Router
  queryClient: QueryClient
  pinia: Pinia
  publicEnv: PublicEnv
}

export function createStudyStackApp(options: StudyStackAppOptions = {}): StudyStackApplication {
  const publicEnv = loadPublicEnv(options.publicEnv)
  const app = createApp(App)
  const router = createStudyStackRouter(options.history)
  const queryClient = options.queryClient ?? new QueryClient()
  const pinia = createPinia()

  app.use(router)
  app.use(VueQueryPlugin, { queryClient })
  app.use(pinia)

  return { app, router, queryClient, pinia, publicEnv }
}
