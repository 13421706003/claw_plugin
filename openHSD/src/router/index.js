import { createRouter, createWebHistory } from 'vue-router'
import ChatView from '../views/ChatView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'

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
      meta: { requiresGuest: true },
    },
    {
      path: '/register',
      name: 'Register',
      component: RegisterView,
      meta: { requiresGuest: true },
    },
    {
      path: '/chat',
      name: 'Chat',
      component: ChatView,
      meta: { requiresAuth: true },
    },
  ],
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const token = localStorage.getItem('openhsd_token') || sessionStorage.getItem('openhsd_token')
  const isLogged = !!token

  if (to.meta.requiresAuth && !isLogged) {
    next({ name: 'Login' })
  } else if (to.meta.requiresGuest && isLogged) {
    next({ name: 'Chat' })
  } else {
    next()
  }
})

export default router
