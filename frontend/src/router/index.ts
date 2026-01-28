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

export default router
