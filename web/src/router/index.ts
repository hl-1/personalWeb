import type { QueryClient } from '@tanstack/vue-query'
import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import type { AuthClient } from '../features/auth/auth-client'
import { authQueryKey, createAuthQueryOptions } from '../features/auth/auth-query'
import type { AuthState } from '../features/auth/auth-schema'
import { createContentClient } from '../features/content/content-client'
import { createPortfolioClient } from '../features/portfolio/portfolio-client'
import {
  consumeReturnTo,
  rememberReturnTo,
  type ReturnToStorage,
} from '../features/auth/return-to'
import FoundationView from '../views/FoundationView.vue'
import HomeView from '../views/HomeView.vue'
import AboutView from '../views/AboutView.vue'
import BlogListView from '../views/BlogListView.vue'
import ArticleDetailView from '../views/ArticleDetailView.vue'
import ProjectListView from '../views/ProjectListView.vue'
import ProjectDetailView from '../views/ProjectDetailView.vue'
import AdminView from '../views/AdminView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import LoginView from '../views/LoginView.vue'
import NotFoundView from '../views/NotFoundView.vue'

const contentClient = createContentClient({ apiBaseUrl: '/api' })
const portfolioClient = createPortfolioClient({ apiBaseUrl: '/api' })

export interface AuthRouteAccess {
  current(): Promise<AuthState>
  refresh(): Promise<AuthState>
  remember(path: string): void
  consume(): string
}

export function createAuthRouteAccess(
  authClient: AuthClient,
  queryClient: QueryClient,
  storage: ReturnToStorage,
): AuthRouteAccess {
  return {
    current: () => queryClient.ensureQueryData(createAuthQueryOptions(authClient)),
    async refresh() {
      await queryClient.invalidateQueries({ queryKey: authQueryKey, exact: true })
      return queryClient.fetchQuery(createAuthQueryOptions(authClient))
    },
    remember: (path) => { rememberReturnTo(storage, path) },
    consume: () => consumeReturnTo(storage),
  }
}

export function createStudyStackRouter(
  history: RouterHistory = createWebHistory(),
  authAccess?: AuthRouteAccess,
) {
  const router = createRouter({
    history,
    routes: [
      {
        path: '/',
        name: 'home',
        component: HomeView,
        props: { contentClient, portfolioClient },
      },
      {
        path: '/about',
        name: 'about',
        component: AboutView,
        props: { portfolioClient },
      },
      {
        path: '/blog',
        name: 'blog',
        component: BlogListView,
        props: { contentClient },
      },
      {
        path: '/blog/:slug',
        name: 'article-detail',
        component: ArticleDetailView,
        props: (route) => ({ contentClient, slug: String(route.params.slug) }),
      },
      {
        path: '/projects',
        name: 'projects',
        component: ProjectListView,
        props: { portfolioClient },
      },
      {
        path: '/projects/:slug',
        name: 'project-detail',
        component: ProjectDetailView,
        props: (route) => ({ portfolioClient, slug: String(route.params.slug) }),
      },
      {
        path: '/foundation',
        name: 'foundation',
        component: FoundationView,
      },
      {
        path: '/login',
        name: 'login',
        component: LoginView,
      },
      {
        path: '/admin',
        name: 'admin',
        component: AdminView,
        meta: { requiresAdmin: true },
      },
      {
        path: '/forbidden',
        name: 'forbidden',
        component: ForbiddenView,
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: NotFoundView,
      },
    ],
  })

  router.beforeEach(async (to) => {
    if (!authAccess) {
      return true
    }

    if (to.name === 'login' && to.query.status === 'success') {
      const state = await authAccess.refresh()
      if (state.authenticated) {
        return authAccess.consume()
      }
      return true
    }

    if (!to.meta.requiresAdmin) {
      return true
    }

    const state = await authAccess.current()
    if (!state.authenticated) {
      authAccess.remember(to.fullPath)
      return { name: 'login' }
    }
    if (!state.user.roles.includes('ADMIN')) {
      return { name: 'forbidden' }
    }
    return true
  })

  return router
}
