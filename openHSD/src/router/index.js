import { createRouter, createWebHistory } from 'vue-router'
import LayoutView from '../views/LayoutView.vue'
import ChatView from '../views/ChatView.vue'
import ConfigView from '../views/ConfigView.vue'
import RechargeView from '../views/RechargeView.vue'
import TestView from '../views/TestView.vue'
import TutorialView from '../views/TutorialView.vue'
import LoginView from '../views/LoginView.vue'
import RegisterView from '../views/RegisterView.vue'
import ProfileView from '../views/ProfileView.vue'

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
      path: '/',
      component: LayoutView,
      meta: { requiresAuth: true },
      children: [
        {
          path: 'chat',
          name: 'Chat',
          component: ChatView,
        },
        {
          path: 'config',
          name: 'Config',
          component: ConfigView,
        },
        {
          path: 'recharge',
          name: 'Recharge',
          component: RechargeView,
        },
        {
          path: 'profile',
          name: 'Profile',
          component: ProfileView,
        },
        {
          path: 'tutorial',
          name: 'Tutorial',
          component: TutorialView,
        },
        {
          path: 'test',
          name: 'Test',
          component: TestView,
        },
      ],
    },
  ],
})

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
