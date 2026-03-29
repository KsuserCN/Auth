import { createRouter, createWebHistory } from 'vue-router'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import MainLayout from '../layouts/MainLayout.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/login',
    },
    {
      path: '/home',
      component: MainLayout,
      children: [
        {
          path: 'profile',
          name: 'profile',
          component: () => import('../views/ProfileView.vue'),
        },
        {
          path: 'overview',
          name: 'overview',
          component: () => import('../views/HomeView.vue'),
        },
        {
          path: 'security',
          name: 'security',
          component: () => import('../views/SecurityView.vue'),
        },
        {
          path: 'login-options',
          name: 'login-options',
          component: () => import('../views/LoginOptionsView.vue'),
        },
        {
          path: 'devices',
          name: 'devices',
          component: () => import('../views/DevicesView.vue'),
        },
        {
          path: 'privacy',
          name: 'privacy',
          component: () => import('../views/PrivacyView.vue'),
        },
        {
          path: 'preferences',
          name: 'preferences',
          component: () => import('../views/PreferencesView.vue'),
        },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('../views/RegisterView.vue'),
    },
    {
      path: '/sensitive-verification',
      name: 'sensitive-verification',
      component: () => import('../views/SensitiveVerificationView.vue'),
    },
    {
      path: '/change-password',
      name: 'change-password',
      component: () => import('../views/ChangePasswordView.vue'),
    },
    {
      path: '/change-email',
      name: 'change-email',
      component: () => import('../views/ChangeEmailView.vue'),
    },
    {
      path: '/totp',
      name: 'totp',
      component: () => import('../views/TotpView.vue'),
    },
    {
      path: '/delete-account',
      name: 'delete-account',
      component: () => import('../views/DeleteAccountView.vue'),
    },
    {
      path: '/add-passkey',
      name: 'add-passkey',
      component: () => import('../views/AddPasskeyView.vue'),
    },
    {
      path: '/oauth/qq/callback',
      name: 'qq-callback',
      meta: { provider: 'qq' },
      component: () => import('../views/OAuthQQCallbackView.vue'),
    },
    {
      path: '/oauth/github/callback',
      name: 'github-callback',
      meta: { provider: 'github' },
      component: () => import('../views/OAuthQQCallbackView.vue'),
    },
    {
      path: '/oauth/microsoft/callback',
      name: 'microsoft-callback',
      meta: { provider: 'microsoft' },
      component: () => import('../views/OAuthQQCallbackView.vue'),
    },
  ],
})

export default router
