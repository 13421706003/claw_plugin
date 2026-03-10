/**
 * 认证接口
 */

import { post } from '../utils/request.js'

/**
 * 登录
 * @returns {Promise<{token, userId, username}>}
 */
export const login = async (username, password) => {
  const data = await post('/api/auth/login', { username, password })
  if (!data.success) throw new Error(data.message || '登录失败')
  return data
}

/**
 * 注册
 * @returns {Promise<{success, message}>}
 */
export const register = async (username, password) => {
  const data = await post('/api/auth/register', { username, password })
  if (!data.success) throw new Error(data.message || '注册失败')
  return data
}
