import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/views/Dashboard.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/scripts',
    name: 'ScriptList',
    component: () => import('@/views/ScriptList.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/script/create',
    name: 'ScriptCreate',
    component: () => import('@/views/ScriptCreate.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/script/:id',
    name: 'ScriptDetail',
    component: () => import('@/views/ScriptDetail.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/script/:id/score',
    name: 'ScoreEntry',
    component: () => import('@/views/ScoreEntry.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN', 'JUDGE'] }
  },
  {
    path: '/assignments',
    name: 'AssignmentList',
    component: () => import('@/views/AssignmentList.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/recusals',
    name: 'RecusalManagement',
    component: () => import('@/views/RecusalManagement.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/my-recusals',
    name: 'MyRecusals',
    component: () => import('@/views/MyRecusals.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN', 'JUDGE'] }
  },
  {
    path: '/judges',
    name: 'JudgeManagement',
    component: () => import('@/views/JudgeManagement.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/statistics',
    name: 'Statistics',
    component: () => import('@/views/Statistics.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/comments',
    name: 'CommentSummary',
    component: () => import('@/views/CommentSummary.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/users',
    name: 'UserManagement',
    component: () => import('@/views/UserManagement.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/Profile.vue'),
    meta: { requiresAuth: true }
  },
  {
    path: '/logs',
    name: 'AuditLogs',
    component: () => import('@/views/AuditLogs.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  },
  {
    path: '/desensitization/audits',
    name: 'DesensitizationAudits',
    component: () => import('@/views/DesensitizationAudits.vue'),
    meta: { requiresAuth: true, roles: ['ADMIN'] }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('token')
  const userData = localStorage.getItem('user')
  const user = userData ? JSON.parse(userData) : null

  if (to.meta.requiresAuth && !token) {
    next('/login')
    return
  }

  if (to.meta.roles && user) {
    const roles = to.meta.roles as string[]
    if (!roles.includes(user.role)) {
      next('/dashboard')
      return
    }
  }

  next()
})

export default router