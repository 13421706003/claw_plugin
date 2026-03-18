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
    // 用户信息和 token 始终持久化，确保刷新页面后数据不丢失
    // remember 参数保留供将来扩展（如区分 token 有效期）
    storage.setToken(data.token)
    storage.setUser(_user.value)
  }

  const logout = () => {
    _token.value = ''
    _user.value  = null
    storage.clear()
  }

  return { token, user, isLogged, setAuth, logout }
}

/**
 * 获取原始 token 字符串（用于复制等操作）
 */
export function getUserToken() {
  return _token.value || ''
}

/**
 * 获取原始用户数据对象（用于直接访问）
 */
export function getUserData() {
  return _user.value || null
}
