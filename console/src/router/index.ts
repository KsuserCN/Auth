import { createRouter, createWebHistory } from 'vue-router'
import { ConsoleApiError } from '@/api/console'
import { useSessionStore } from '@/stores/session'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue') },
    {
      path: '/auth/callback',
      component: () => import('@/views/CallbackView.vue'),
    },
    {
      path: '/auth/verification-callback',
      component: () => import('@/views/CallbackView.vue'),
      meta: { verification: true },
    },
    {
      path: '/verification',
      component: () => import('@/views/VerificationView.vue'),
      meta: { protected: true },
    },
    {
      path: '/',
      component: () => import('@/components/ConsoleLayout.vue'),
      meta: { protected: true },
      children: [
        { path: '', redirect: '/users' },
        { path: 'users', component: () => import('@/views/UsersView.vue') },
        { path: 'user-applications', component: () => import('@/views/UserApplicationsView.vue') },
        { path: 'unionid-applications', component: () => import('@/views/UnionidApplicationsView.vue') },
        {
          path: 'applications',
          component: () => import('@/views/ApplicationsView.vue'),
        },
        { path: 'audit', component: () => import('@/views/AuditView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/users' },
  ],
})
router.beforeEach(async (to) => {
  if (!to.matched.some((record) => record.meta.protected)) return
  const store = useSessionStore()
  try {
    const session = await store.load()
    if (session.user.systemRole !== 'SUPER_ADMIN') {
      store.clear()
      return {
        path: '/login',
        query: { error: 'CONSOLE_ACCESS_DENIED' },
      }
    }
  } catch (error) {
    store.clear()
    return {
      path: '/login',
      query: {
        error: error instanceof ConsoleApiError ? error.errorCode : 'NETWORK_ERROR',
      },
    }
  }
})
export default router
