/**
 * 消息接口
 */

import { get, post, del } from '../utils/request.js'

/**
 * 获取聊天历史
 */
export const getMessages = (userId, clawId) =>
  get(`/api/messages?userId=${userId}&clawId=${clawId}`)

/**
 * 清空聊天历史
 */
export const deleteMessages = (userId, clawId) =>
  del(`/api/messages?userId=${userId}&clawId=${clawId}`)

/**
 * 发送消息给 AI
 * @param {object} payload { userId, messageId, content, clawId, attachments }
 */
export const sendMessage = (payload) =>
  post('/api/claw/send', payload)
