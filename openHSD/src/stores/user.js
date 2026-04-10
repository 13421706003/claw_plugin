import { ref, computed } from 'vue'
import { login as loginApi } from '../api/auth.js'
import { clearMessages } from '../api/aiService.js'

const TOKEN_KEY = 'openhsd_token'
const USER_KEY  = 'openhsd_user'

// 从本地存储恢复状态
const _token = ref(localStorage.getItem(TOKEN_KEY) || sessionStorage.getItem(TOKEN_KEY) || '')
const _user  = ref(JSON.parse(localStorage.getItem(USER_KEY) || sessionStorage.getItem(USER_KEY) || 'null'))

export const useUserStore = () => {
  const token    = computed(() => _token.value)
  const user     = computed(() => _user.value)
  const isLogged = computed(() => !!_token.value)
  const username = computed(() => _user.value?.username || '')

  /**
   * 登录
   * @param {string} username
   * @param {string} password
   * @param {boolean} remember  是否记住我（localStorage vs sessionStorage）
   */
  const login = async (username, password, remember = false) => {
    const data = await loginApi(username, password)

    _token.value = data.token
    _user.value  = { userId: data.userId, username: data.username }

    // 先清除两个存储，避免残留旧数据
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)

    const storage = remember ? localStorage : sessionStorage
    storage.setItem(TOKEN_KEY, data.token)
    storage.setItem(USER_KEY, JSON.stringify(_user.value))
  }

  /**
   * 设置用户名
   * @param {string} newUsername
   */
  const setUsername = (newUsername) => {
    if (_user.value) {
      _user.value = { ..._user.value, username: newUsername }
      
      // 更新存储
      const storage = localStorage.getItem(USER_KEY) ? localStorage : sessionStorage
      storage.setItem(USER_KEY, JSON.stringify(_user.value))
    }
  }

  const logout = () => {
    clearMessages()
    _token.value = ''
    _user.value  = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    sessionStorage.removeItem(TOKEN_KEY)
    sessionStorage.removeItem(USER_KEY)
  }

  return { token, user, isLogged, username, login, setUsername, logout }
}
