import { ref } from 'vue'

let ws = null
let reconnectTimer = null
let retryCount = 0
let currentUserId = null  // 当前连接的 userId

const isConnected = ref(false)

/** 消息回调：(type, data) => void */
let onMessage = null

/**
 * 从 localStorage / sessionStorage 读取当前登录用户 ID
 */
function getStoredUserId() {
  try {
    const raw = localStorage.getItem('openhsd_user') || sessionStorage.getItem('openhsd_user')
    if (!raw) return null
    const user = JSON.parse(raw)
    return user?.userId ?? null
  } catch {
    return null
  }
}

function buildWsUrl(userId) {
  return `ws://${window.location.host}/ws/web/${userId}`
}

export function useWebSocket() {
  const connect = () => {
    const userId = getStoredUserId()
    if (!userId) {
      console.warn('[WebWS] 未登录，跳过连接')
      return
    }

    // 如果 userId 变了（切换账号），关闭旧连接
    if (ws && currentUserId !== userId) {
      ws.close()
      ws = null
    }

    if (ws && ws.readyState === WebSocket.OPEN) return

    currentUserId = userId
    const url = buildWsUrl(userId)
    console.log(`[WebWS] 正在连接：${url}（第 ${retryCount} 次尝试）`)
    ws = new WebSocket(url)

    ws.onopen = () => {
      console.log('[WebWS] 连接成功')
      isConnected.value = true
      retryCount = 0
      startHeartbeat()
    }

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)
        const type = data.type

        if (type === 'pong') {
          console.debug('[WebWS] pong 收到')
          return
        }
        if (type === 'connected') {
          console.log('[WebWS] 服务器确认连接：userId=', data.userId)
          return
        }

        if (onMessage) {
          onMessage(type, data)
        }
      } catch (e) {
        console.error('[WebWS] 解析消息失败：', e)
      }
    }

    ws.onclose = (event) => {
      console.warn(`[WebWS] 连接关闭：code=${event.code}`)
      isConnected.value = false
      clearHeartbeat()
      // 只有正常断开（非主动关闭）才重连
      if (event.code !== 1000) {
        scheduleReconnect()
      }
    }

    ws.onerror = (error) => {
      console.error('[WebWS] 连接错误：', error)
    }
  }

  const disconnect = () => {
    clearHeartbeat()
    clearReconnectTimer()
    if (ws) {
      ws.close(1000, 'user logout')
      ws = null
    }
    isConnected.value = false
    currentUserId = null
  }

  const send = (data) => {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      console.warn('[WebWS] 发送失败：连接未就绪')
      return false
    }
    ws.send(JSON.stringify(data))
    return true
  }

  return {
    isConnected,
    connect,
    disconnect,
    send,
    setOnMessage: (callback) => { onMessage = callback }
  }
}

let heartbeatTimer = null
const startHeartbeat = () => {
  clearHeartbeat()
  heartbeatTimer = setInterval(() => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'ping' }))
    }
  }, 30000)
}

const clearHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

const scheduleReconnect = () => {
  if (reconnectTimer) return
  const delay = Math.min(1000 * Math.pow(2, retryCount), 60000)
  retryCount++
  console.log(`[WebWS] ${delay / 1000}s 后重连（第 ${retryCount} 次）`)
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null
    connect()
  }, delay)
}

const clearReconnectTimer = () => {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}
