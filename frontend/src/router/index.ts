import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    component: () => import('@/views/Landing.vue')
  },
  {
    path: '/login',
    component: () => import('@/views/Login.vue')
  },
  {
    path: '/app',
    meta: { requiresAuth: true },
    component: () => import('@/views/AppLayout.vue'),
    redirect: '/app/chat',
    children: [
      {
        path: 'chat',
        component: () => import('@/views/ChatLayout.vue')
      },
      {
        path: 'square',
        component: () => import('@/views/Square.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  if (to.meta.requiresAuth) {
    const token = localStorage.getItem('token')
    if (!token) {
      next('/login')
      return
    }
  }
  next()
})

export default router
