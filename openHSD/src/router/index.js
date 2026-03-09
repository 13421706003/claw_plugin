import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import LoginView from '../views/LoginView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/login',
      name: 'Login',
      component: LoginView,
      meta: { requiresGuest: true }, // 已登录用户不能访问登录页
    },
    {
      path: '/chat',
      name: 'Chat',
      component: ChatView,
      meta: { requiresAuth: true }, // 必须登录才能访问
    },
  ],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('openhsd_token') || sessionStorage.getItem('openhsd_token')
  const isLogged = !!token

  if (to.meta.requiresAuth && !isLogged) {
    // 未登录，跳转到登录页
    next({ name: 'Login' })
  } else if (to.meta.requiresGuest && isLogged) {
    // 已登录，跳转到聊天页
    next({ name: 'Chat' })
  } else {
    next()
  }
})

export default router
