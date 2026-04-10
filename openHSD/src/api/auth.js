const API_BASE = import.meta.env.VITE_API_BASE

/**
 * 登录接口
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{token: string, userId: string, username: string}>}
 */
export const login = async (username, password) => {
  const res = await fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  const data = await res.json()

  if (!res.ok || !data.success) {
    throw new Error(data.message || '登录失败')
  }

  return data
}

/**
 * 注册接口
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{success: boolean, message: string}>}
 */
export const register = async (username, password) => {
  const res = await fetch(`${API_BASE}/auth/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  })

  const data = await res.json()

  if (!res.ok || !data.success) {
    throw new Error(data.message || '注册失败')
  }

  return data
}

/**
 * 修改用户名
 * @param {string} token - JWT Token
 * @param {string} newUsername - 新用户名
 * @returns {Promise<{success: boolean, message: string}>}
 */
export const updateUsername = async (token, newUsername) => {
  console.log('[auth.js] updateUsername called with token:', token)
  console.log('[auth.js] Token type:', typeof token)
  console.log('[auth.js] Token length:', token?.length)
  
  if (!token || typeof token !== 'string' || token.trim() === '') {
    throw new Error('Token 无效，请重新登录')
  }
  
  const res = await fetch(`${API_BASE}/auth/username`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ newUsername }),
  })

  const data = await res.json()

  if (!res.ok || !data.success) {
    throw new Error(data.message || '修改用户名失败')
  }

  return data
}

/**
 * 修改密码
 * @param {string} token - JWT Token
 * @param {string} oldPassword - 旧密码
 * @param {string} newPassword - 新密码
 * @returns {Promise<{success: boolean, message: string}>}
 */
export const updatePassword = async (token, oldPassword, newPassword) => {
  const res = await fetch(`${API_BASE}/auth/password`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({ oldPassword, newPassword }),
  })

  const data = await res.json()

  if (!res.ok || !data.success) {
    throw new Error(data.message || '修改密码失败')
  }

  return data
}
