import { ref } from 'vue'

const WS_URL = `ws://${window.location.host}/ws/web/user001`

let ws = null
let reconnectTimer = null
let retryCount = 0

const isConnected = ref(false)

/** 消息回调：(type, data) => void */
let onMessage = null

export function useWebSocket() {
  const connect = () => {
    if (ws && ws.readyState === WebSocket.OPEN) return

    console.log(`[WebWS] 正在连接：${WS_URL}（第 ${retryCount} 次尝试）`)
    ws = new WebSocket(WS_URL)

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
      scheduleReconnect()
    }

    ws.onerror = (error) => {
      console.error('[WebWS] 连接错误：', error)
    }
  }

  const disconnect = () => {
    clearHeartbeat()
    clearReconnectTimer()
    if (ws) {
      ws.close()
      ws = null
    }
    isConnected.value = false
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
