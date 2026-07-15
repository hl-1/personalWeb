import { createRouter, createWebHistory, type RouterHistory } from 'vue-router'
import FoundationView from '../views/FoundationView.vue'
import HomeView from '../views/HomeView.vue'
import NotFoundView from '../views/NotFoundView.vue'

export function createStudyStackRouter(history: RouterHistory = createWebHistory()) {
  return createRouter({
    history,
    routes: [
      {
        path: '/',
        name: 'home',
        component: HomeView,
      },
      {
        path: '/foundation',
        name: 'foundation',
        component: FoundationView,
      },
      {
        path: '/:pathMatch(.*)*',
        name: 'not-found',
        component: NotFoundView,
      },
    ],
  })
}
