/**
 * 本地存储工具（封装 uni.setStorageSync / uni.getStorageSync）
 * 兼容 App / H5 / 小程序
 */

const TOKEN_KEY = 'openhsd_token'
const USER_KEY  = 'openhsd_user'

export const storage = {
  setToken(token) {
    try { uni.setStorageSync(TOKEN_KEY, token) } catch (e) {}
  },
  getToken() {
    try { return uni.getStorageSync(TOKEN_KEY) || '' } catch (e) { return '' }
  },
  removeToken() {
    try { uni.removeStorageSync(TOKEN_KEY) } catch (e) {}
  },

  setUser(user) {
    try { uni.setStorageSync(USER_KEY, JSON.stringify(user)) } catch (e) {}
  },
  getUser() {
    try {
      const raw = uni.getStorageSync(USER_KEY)
      return raw ? JSON.parse(raw) : null
    } catch (e) { return null }
  },
  removeUser() {
    try { uni.removeStorageSync(USER_KEY) } catch (e) {}
  },

  clear() {
    this.removeToken()
    this.removeUser()
  }
}
