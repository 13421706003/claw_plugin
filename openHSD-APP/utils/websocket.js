/**
 * WebSocket 客户端（uni.connectSocket）
 * 支持：
 *   - 自动 ping/pong 心跳（30s）
 *   - 指数退避重连（最大 60s）
 *   - response_chunk 流式 AI 回复
 *   - 主动断开不重连
 */

import { ref } from 'vue'
import { storage } from './storage.js'

const WS_BASE = 'ws://192.168.110.129:9081'

let socketTask     = null
let heartbeatTimer = null
let reconnectTimer = null
let retryCount     = 0
let currentUserId  = null
let onMessageCb    = null
let manualClose    = false

export const isConnected = ref(false)

function buildUrl(userId) {
  return `${WS_BASE}/ws/web/${userId}`
}

export function setOnMessage(callback) {
  onMessageCb = callback
}

export function connect() {
  const userId = storage.getUser()?.userId
  if (!userId) {
    console.warn('[WS] 未登录，跳过连接')
    return
  }

  // 若 userId 更换，先关闭旧连接
  if (socketTask && currentUserId !== userId) {
    manualClose = true
    socketTask.close({ code: 1000 })
    socketTask = null
  }

  if (socketTask) return  // 已有连接

  manualClose    = false
  currentUserId  = userId
  const url      = buildUrl(userId)
  console.log(`[WS] 连接：${url}（第 ${retryCount} 次）`)

  socketTask = uni.connectSocket({
    url,
    complete: () => {}
  })

  socketTask.onOpen(() => {
    console.log('[WS] 已连接')
    isConnected.value = true
    retryCount = 0
    startHeartbeat()
  })

  socketTask.onMessage((event) => {
    try {
      const data = JSON.parse(event.data)
      const type = data.type
      if (type === 'pong') return
      if (type === 'connected') {
        console.log('[WS] 服务器确认：userId=', data.userId)
        return
      }
      if (onMessageCb) onMessageCb(type, data)
    } catch (e) {
      console.error('[WS] 消息解析失败：', e)
    }
  })

  socketTask.onClose((res) => {
    console.warn(`[WS] 关闭：code=${res.code}`)
    isConnected.value = false
    clearHeartbeat()
    socketTask = null
    if (!manualClose && res.code !== 1000) {
      scheduleReconnect()
    }
  })

  socketTask.onError((err) => {
    console.error('[WS] 错误：', err)
    isConnected.value = false
  })
}

export function disconnect() {
  manualClose = true
  clearHeartbeat()
  clearReconnectTimer()
  if (socketTask) {
    socketTask.close({ code: 1000, reason: 'logout' })
    socketTask = null
  }
  isConnected.value = false
  currentUserId = null
}

export function sendRaw(data) {
  if (!socketTask) return false
  try {
    socketTask.send({ data: JSON.stringify(data) })
    return true
  } catch (e) {
    return false
  }
}

function startHeartbeat() {
  clearHeartbeat()
  heartbeatTimer = setInterval(() => {
    sendRaw({ type: 'ping' })
  }, 30000)
}

function clearHeartbeat() {
  if (heartbeatTimer) { clearInterval(heartbeatTimer); heartbeatTimer = null }
}

function scheduleReconnect() {
  if (reconnectTimer) return
  const delay = Math.min(1000 * Math.pow(2, retryCount), 60000)
  retryCount++
  console.log(`[WS] ${delay / 1000}s 后重连（第 ${retryCount} 次）`)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

function clearReconnectTimer() {
  if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null }
}
