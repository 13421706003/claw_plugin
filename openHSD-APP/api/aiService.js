/**
 * AI 消息服务（模块级单例）
 * 管理：消息列表、loading 状态、当前设备
 * 处理：WebSocket 流式响应
 */

import { ref } from 'vue'
import { storage } from '../utils/storage.js'
import { isConnected, connect, disconnect, setOnMessage, sendRaw } from '../utils/websocket.js'
import { getMessages, deleteMessages, sendMessage as apiSend } from './message.js'

let msgCounter = 0
const nextId = () => `msg_${Date.now()}_${++msgCounter}`

export const loading        = ref(false)
export const messages       = ref([])
export const currentClawId  = ref(null)

export { isConnected, connect, disconnect }

const pendingContent = new Map()

// ===== WebSocket 消息处理 =====
setOnMessage((type, data) => {
  const { messageId, chunk, result } = data

  if (type === 'response_chunk') {
    const text = extractText(chunk)
    if (text) {
      const prev = pendingContent.get(messageId) || ''
      const next = prev + text
      pendingContent.set(messageId, next)
      updateAssistant(messageId, next, false)
    }
  }

  if (type === 'response') {
    const text = extractText(result) || pendingContent.get(messageId) || ''
    updateAssistant(messageId, text, true)
    pendingContent.delete(messageId)
    loading.value = false
  }
})

// ===== 文本提取 =====
function extractText(content) {
  if (!content) return ''
  if (typeof content === 'string') {
    try {
      const parsed = JSON.parse(content)
      if (Array.isArray(parsed)) return parseBlocks(parsed)
    } catch {}
    return content
  }
  if (Array.isArray(content)) return parseBlocks(content)
  return ''
}

function parseBlocks(blocks) {
  return blocks.map(b => {
    switch (b.type) {
      case 'text':  return b.text || ''
      case 'image': return b.url ? `![image](${b.url})` : ''
      case 'code':  return `\`\`\`${b.language || ''}\n${b.text || ''}\n\`\`\``
      default:      return b.text || ''
    }
  }).join('\n\n')
}

// ===== 消息更新 =====
function updateAssistant(messageId, content, isFinal) {
  const idx = messages.value.findIndex(m => m.messageId === messageId && m.role === 'assistant')
  if (idx >= 0) {
    messages.value[idx].content = content
    messages.value[idx].loading = !isFinal
  } else {
    messages.value.push({ messageId, role: 'assistant', content, loading: !isFinal })
  }
}

// ===== 加载历史 =====
export async function loadHistory(clawId) {
  const user = storage.getUser()
  if (!user?.userId || !clawId) { messages.value = []; return }
  try {
    const data = await getMessages(user.userId, clawId)
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

// ===== 清空历史 =====
export async function clearHistory() {
  const user = storage.getUser()
  if (!user?.userId || !currentClawId.value) return
  try {
    await deleteMessages(user.userId, currentClawId.value)
    messages.value = []
  } catch (e) {
    console.error('[aiService] 清空历史失败：', e)
  }
}

// ===== 切换设备 =====
export async function selectClaw(clawId) {
  if (currentClawId.value === clawId) return
  currentClawId.value = clawId
  await loadHistory(clawId)
}

// ===== 发送消息 =====
export async function send(content, attachments = []) {
  if ((!content && attachments.length === 0) || loading.value) return

  const user = storage.getUser()
  if (!user?.userId) { console.error('[aiService] 未登录'); return }
  if (!currentClawId.value) { console.error('[aiService] 未选择设备'); return }

  // 确保 WS 已连接
  if (!isConnected.value) {
    connect()
    await new Promise(r => setTimeout(r, 800))
  }

  loading.value = true
  const messageId = nextId()

  messages.value.push({ messageId, role: 'user', content, attachments })
  messages.value.push({ messageId, role: 'assistant', content: '', loading: true })
  pendingContent.set(messageId, '')

  const formattedAttachments = attachments.map(a => ({
    type: 'image', base64: a.base64, name: a.name
  }))

  try {
    const data = await apiSend({
      userId: user.userId,
      messageId,
      content,
      clawId: currentClawId.value,
      attachments: formattedAttachments
    })
    if (!data.success) {
      updateAssistant(messageId, data.message || '发送失败', true)
      loading.value = false
    }
  } catch (e) {
    console.error('[aiService] 发送失败：', e)
    updateAssistant(messageId, '网络错误，请重试', true)
    loading.value = false
  }
}
