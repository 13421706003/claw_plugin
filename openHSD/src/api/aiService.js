import { ref } from 'vue'
import { useWebSocket } from './websocket.js'

const API_BASE = '/api'

let msgCounter = 0
const nextMessageId = () => `msg_${Date.now()}_${++msgCounter}`

const loading = ref(false)
const messages = ref([])
const currentClawId = ref(null)

const pendingContent = new Map()

const { isConnected, connect, disconnect, setOnMessage } = useWebSocket()

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

setOnMessage((type, data) => {
  const { messageId, chunk, result, status } = data

  if (type === 'response_chunk') {
    const text = extractText(chunk)
    if (text) {
      pendingContent.set(messageId, text)
      updateAssistantMessage(messageId, text, false)
    }
  }

  if (type === 'response') {
    const text = extractText(result) || pendingContent.get(messageId) || ''
    updateAssistantMessage(messageId, text, true)
    pendingContent.delete(messageId)
    loading.value = false
  }
})

const extractText = (content) => {
  if (!content) return ''
  
  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content)
      if (Array.isArray(parsed)) {
        return parseContentBlocks(parsed)
      }
    } catch { }
    return content
  }
  
  if (Array.isArray(content)) {
    return parseContentBlocks(content)
  }
  
  return ''
}

const parseContentBlocks = (blocks) => {
  return blocks.map(block => {
    switch (block.type) {
      case 'text':
        return block.text || ''
      case 'image':
        const url = block.url || block.image?.url || ''
        return url ? `![image](${url})` : ''
      case 'code':
        return `\`\`\`${block.language || ''}\n${block.text || ''}\n\`\`\``
      default:
        return block.text || ''
    }
  }).join('\n\n')
}

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

const loadHistory = async (clawId) => {
  const userId = getStoredUserId()
  if (!userId || !clawId) {
    messages.value = []
    return
  }

  try {
    const res = await fetch(`${API_BASE}/messages?userId=${userId}&clawId=${clawId}`)
    const data = await res.json()
    if (data.success && Array.isArray(data.messages)) {
      messages.value = data.messages.map(m => ({
        messageId: m.messageId,
        role: m.role,
        content: extractText(m.content),
        loading: false
      }))
    } else {
      messages.value = []
    }
  } catch (e) {
    console.error('[aiService] 加载历史失败：', e)
    messages.value = []
  }
}

const clearHistory = async () => {
  const userId = getStoredUserId()
  if (!userId || !currentClawId.value) return

  try {
    await fetch(`${API_BASE}/messages?userId=${userId}&clawId=${currentClawId.value}`, {
      method: 'DELETE'
    })
    messages.value = []
  } catch (e) {
    console.error('[aiService] 清空历史失败：', e)
  }
}

const selectClaw = async (clawId) => {
  if (currentClawId.value === clawId) return
  currentClawId.value = clawId
  await loadHistory(clawId)
}

const sendMessage = async (content, attachments = []) => {
  if ((!content && attachments.length === 0) || loading.value) return

  const userId = getStoredUserId()
  if (!userId) {
    console.error('[aiService] 未登录，无法发送消息')
    return
  }

  if (!currentClawId.value) {
    console.error('[aiService] 未选择设备，无法发送消息')
    return
  }

  if (!isConnected.value) {
    connect()
    await new Promise(resolve => setTimeout(resolve, 800))
  }

  loading.value = true
  const messageId = nextMessageId()

  messages.value.push({ messageId, role: 'user', content, attachments })
  messages.value.push({ messageId, role: 'assistant', content: '', loading: true })
  pendingContent.set(messageId, '')

  const formattedAttachments = attachments.map(att => ({
    type: 'image',
    base64: att.base64,
    name: att.name
  }))

  try {
    const res = await fetch(`${API_BASE}/claw/send`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId,
        messageId,
        content,
        clawId: currentClawId.value,
        attachments: formattedAttachments
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

export {
  loading,
  messages,
  currentClawId,
  sendMessage,
  loadHistory,
  clearHistory,
  selectClaw,
  isConnected,
  connect,
  disconnect
}
