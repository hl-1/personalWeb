import type { QueryClient } from '@tanstack/vue-query'
import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import type { AuthClient } from '../features/auth/auth-client'
import { authQueryKey, createAuthQueryOptions } from '../features/auth/auth-query'
import type { AuthState } from '../features/auth/auth-schema'
import { createAdminClient } from '../features/admin/admin-client'
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
import AdminDashboardView from '../views/admin/AdminDashboardView.vue'
import AdminArticleEditView from '../views/admin/AdminArticleEditView.vue'
import AdminArticleListView from '../views/admin/AdminArticleListView.vue'
import AdminNotFoundView from '../views/admin/AdminNotFoundView.vue'
import AdminTaxonomyView from '../views/admin/AdminTaxonomyView.vue'
import AdminProjectEditView from '../views/admin/AdminProjectEditView.vue'
import AdminProjectListView from '../views/admin/AdminProjectListView.vue'
import AdminProfileView from '../views/admin/AdminProfileView.vue'
import AdminSkillsView from '../views/admin/AdminSkillsView.vue'
import AdminExperiencesView from '../views/admin/AdminExperiencesView.vue'
import ForbiddenView from '../views/ForbiddenView.vue'
import LoginView from '../views/LoginView.vue'
import NotFoundView from '../views/NotFoundView.vue'

const contentClient = createContentClient({ apiBaseUrl: '/api' })
const portfolioClient = createPortfolioClient({ apiBaseUrl: '/api' })
const adminClient = createAdminClient({ apiBaseUrl: '/api' })

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
        component: AdminView,
        meta: { requiresAdmin: true, admin: true, title: 'Administration' },
        children: [
          {
            path: '',
            name: 'admin-dashboard',
            component: AdminDashboardView,
            meta: { title: 'Dashboard', breadcrumb: 'Dashboard' },
          },
          {
            path: 'articles',
            name: 'admin-articles',
            component: AdminArticleListView,
            props: { adminClient },
            meta: { title: 'Articles', breadcrumb: 'Articles' },
          },
          {
            path: 'articles/new',
            name: 'admin-article-new',
            component: AdminArticleEditView,
            props: { adminClient },
            meta: { title: 'New article', breadcrumb: 'New' },
          },
          {
            path: 'articles/:id',
            name: 'admin-article-edit',
            component: AdminArticleEditView,
            props: (route) => ({ id: String(route.params.id), adminClient }),
            meta: { title: 'Edit article', breadcrumb: 'Edit' },
          },
          {
            path: 'categories', name: 'admin-categories', component: AdminTaxonomyView,
            props: { kind: 'category', adminClient }, meta: { title: 'Categories', breadcrumb: 'Categories' },
          },
          {
            path: 'tags', name: 'admin-tags', component: AdminTaxonomyView,
            props: { kind: 'tag', adminClient }, meta: { title: 'Tags', breadcrumb: 'Tags' },
          },
          {
            path: 'portfolio/projects', name: 'admin-projects', component: AdminProjectListView,
            props: { adminClient }, meta: { title: 'Projects', breadcrumb: 'Projects' },
          },
          {
            path: 'portfolio/projects/new', name: 'admin-project-new', component: AdminProjectEditView,
            props: { adminClient }, meta: { title: 'New project', breadcrumb: 'New' },
          },
          {
            path: 'portfolio/projects/:id', name: 'admin-project-edit', component: AdminProjectEditView,
            props: (route) => ({ id: String(route.params.id), adminClient }),
            meta: { title: 'Edit project', breadcrumb: 'Edit' },
          },
          {
            path: 'portfolio/profile', name: 'admin-profile', component: AdminProfileView,
            props: { adminClient }, meta: { title: 'Profile', breadcrumb: 'Profile' },
          },
          {
            path: 'portfolio/skills', name: 'admin-skills', component: AdminSkillsView,
            props: { adminClient }, meta: { title: 'Skills', breadcrumb: 'Skills' },
          },
          {
            path: 'portfolio/experiences', name: 'admin-experiences', component: AdminExperiencesView,
            props: { adminClient }, meta: { title: 'Experience', breadcrumb: 'Experience' },
          },
          {
            path: ':pathMatch(.*)*',
            name: 'admin-not-found',
            component: AdminNotFoundView,
            meta: { title: 'Page not found', breadcrumb: 'Not found' },
          },
        ],
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
