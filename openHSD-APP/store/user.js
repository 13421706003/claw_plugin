/**
 * 用户状态管理（模块级单例 reactive store）
 * 持久化到 uni.setStorageSync
 */

import { ref, computed } from 'vue'
import { storage } from '../utils/storage.js'

// 从本地存储恢复
const _token = ref(storage.getToken())
const _user  = ref(storage.getUser())

export function useUserStore() {
  const token    = computed(() => _token.value)
  const user     = computed(() => _user.value)
  const isLogged = computed(() => !!_token.value)

  const setAuth = (data, remember = true) => {
    _token.value = data.token
    _user.value  = { userId: data.userId, username: data.username }
    if (remember) {
      storage.setToken(data.token)
      storage.setUser(_user.value)
    }
  }

  const logout = () => {
    _token.value = ''
    _user.value  = null
    storage.clear()
  }

  return { token, user, isLogged, setAuth, logout }
}
