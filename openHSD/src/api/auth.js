const API_BASE = '/api'

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
