import { ref } from 'vue'
import { useWebSocket } from './websocket.js'

const API_BASE = '/api'
const USER_ID = 'user001'

let msgCounter = 0
const nextMessageId = () => `msg_${Date.now()}_${++msgCounter}`

const loading = ref(false)
const messages = ref([])

// messageId -> 累积的 assistant 内容
const pendingContent = new Map()

// WebSocket 连接
const { isConnected, connect, disconnect, setOnMessage } = useWebSocket()

// 处理来自后端的 WS 消息
setOnMessage((type, data) => {
  const { messageId, chunk, result, status } = data

  if (type === 'response_chunk') {
    // chunk 是 OpenClaw 的 content block 数组: [{"type":"text","text":"..."}]
    const text = extractText(chunk)
    if (text) {
      pendingContent.set(messageId, text)
      updateAssistantMessage(messageId, text, false)
    }
  }

  if (type === 'response') {
    // 最终结果
    const text = extractText(result) || pendingContent.get(messageId) || ''
    updateAssistantMessage(messageId, text, true)
    pendingContent.delete(messageId)
    loading.value = false
  }
})

// 从 content block 数组中提取 text
const extractText = (content) => {
  if (!content) return ''
  if (typeof content === 'string') return content
  if (Array.isArray(content)) {
    const textBlock = content.find(b => b.type === 'text')
    return textBlock?.text || ''
  }
  return ''
}

// 更新或追加 assistant 消息
const updateAssistantMessage = (messageId, content, isFinal) => {
  const idx = messages.value.findIndex(m => m.messageId === messageId && m.role === 'assistant')
  if (idx >= 0) {
    messages.value[idx].content = content
    messages.value[idx].loading = !isFinal
  } else {
    messages.value.push({
      messageId,
      role: 'assistant',
      content,
      loading: !isFinal
    })
  }
}

// 发送消息
const sendMessage = async (content) => {
  if (!content || loading.value) return

  // 确保 WS 连接
  if (!isConnected.value) {
    connect()
    await new Promise(resolve => setTimeout(resolve, 500))
  }

  loading.value = true
  const messageId = nextMessageId()

  // 添加用户消息
  messages.value.push({
    messageId,
    role: 'user',
    content
  })

  // 初始化空 assistant 消息（loading 状态）
  messages.value.push({
    messageId,
    role: 'assistant',
    content: '',
    loading: true
  })
  pendingContent.set(messageId, '')

  // 发送到后端
  try {
    const res = await fetch(`${API_BASE}/claw/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: USER_ID,
        messageId,
        content
      })
    })

    const data = await res.json()
    if (!data.success) {
      updateAssistantMessage(messageId, data.message || '发送失败', true)
      loading.value = false
    }
  } catch (e) {
    console.error('[aiService] 发送失败：', e)
    updateAssistantMessage(messageId, '网络错误，请重试', true)
    loading.value = false
  }
}

// 初始化连接
connect()

export {
  loading,
  messages,
  sendMessage,
  isConnected,
  connect,
  disconnect
}
