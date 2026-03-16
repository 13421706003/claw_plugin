import { ref } from 'vue'
import { useWebSocket } from './websocket.js'

const API_BASE = import.meta.env.VITE_API_BASE

let msgCounter = 0
const nextMessageId = () => `msg_${Date.now()}_${++msgCounter}`

const loading = ref(false)
const messages = ref([])
const currentClawId = ref(null)

/** 广播模式下，记录有多少台设备还在回复中 */
let pendingBroadcastCount = 0

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

    // 广播模式：等所有设备都回复完才解除 loading
    if (pendingBroadcastCount > 0) {
      pendingBroadcastCount--
      if (pendingBroadcastCount <= 0) {
        pendingBroadcastCount = 0
        loading.value = false
      }
    } else {
      loading.value = false
    }
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
      messages.value = data.messages.map(m => {
        // 解析历史消息中的附件（后端已将 objectKey 替换为公开 URL）
        let attachments = []
        if (m.attachments) {
          try {
            const parsed = JSON.parse(m.attachments)
            if (Array.isArray(parsed)) {
              attachments = parsed
                .filter(a => a.url)
                .map(a => {
                  // 判断是否图片：优先用 type，其次从 objectKey/name 扩展名判断
                  const imageExts = ['jpg','jpeg','png','gif','webp','bmp','svg']
                  const ext = (a.objectKey || a.name || '').split('.').pop().toLowerCase()
                  const isImage = (a.type && a.type.startsWith('image/')) ||
                                  imageExts.includes(ext)
                  // 如果是图片但 type 为空，补全 type
                  const resolvedType = a.type || (isImage ? `image/${ext === 'jpg' ? 'jpeg' : ext}` : 'application/octet-stream')
                  return {
                    uid:       a.objectKey,
                    name:      a.name,
                    type:      resolvedType,
                    size:      a.size,
                    objectKey: a.objectKey,
                    url:       a.url,
                    // 图片：用 MinIO URL 作为图片 src
                    // 文件：base64 为 null，渲染时走文件卡片路径
                    base64:    isImage ? a.url : null,
                  }
                })
            }
          } catch (e) {
            console.warn('[aiService] 附件解析失败：', e)
          }
        }
        return {
          messageId: m.messageId,
          role: m.role,
          content: extractText(m.content),
          attachments,
          loading: false
        }
      })
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
  // 广播模式不加载历史
  if (clawId === '__ALL__') {
    messages.value = []
    return
  }
  await loadHistory(clawId)
}

const sendMessage = async (content, attachments = [], clawList = []) => {
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

  // 广播模式
  if (currentClawId.value === '__ALL__') {
    await sendBroadcast(userId, content, attachments, clawList)
    return
  }

  // 单设备模式
  loading.value = true
  const messageId = nextMessageId()

  messages.value.push({ messageId, role: 'user', content, attachments })
  messages.value.push({ messageId, role: 'assistant', content: '', loading: true })
  pendingContent.set(messageId, '')

  // 格式化附件：图片需要同时传 objectKey 和 base64，文档只传 objectKey
  const formattedAttachments = attachments.map(att => {
    const isImage = att.type && att.type.startsWith('image/')
    if (att.objectKey) {
      // 预上传文件
      if (isImage) {
        // 图片：需要同时传 objectKey（用于后端存储）和 base64（用于 OpenClaw）
        return { objectKey: att.objectKey, base64: att.base64, name: att.name, type: att.type, size: att.size }
      }
      // 文档：只传 objectKey，后端生成 URL 给 OpenClaw 下载
      return { objectKey: att.objectKey, name: att.name, type: att.type, size: att.size }
    }
    // 兼容旧的粘贴图片（没有 objectKey）
    return { type: att.type || 'image/png', base64: att.base64, name: att.name }
  })

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

/**
 * 广播模式：向所有在线设备发送消息
 */
const sendBroadcast = async (userId, content, attachments, clawList) => {
  loading.value = true
  const messageId = nextMessageId()

  // 推入用户消息
  messages.value.push({ messageId, role: 'user', content, attachments })

  const formattedAttachments = attachments.map(att => {
    if (att.objectKey) {
      return { objectKey: att.objectKey, name: att.name, type: att.type, size: att.size }
    }
    return { type: att.type || 'image/png', base64: att.base64, name: att.name }
  })

  try {
    const res = await fetch(`${API_BASE}/claw/broadcast`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId,
        messageId,
        content,
        attachments: formattedAttachments
      })
    })

    const data = await res.json()

    if (!data.success) {
      messages.value.push({ messageId, role: 'assistant', content: data.message || '广播失败', loading: false })
      loading.value = false
      return
    }

    // 为每台成功发送的设备创建 assistant 占位消息
    const sentClawIds = data.sentClawIds || []
    const sentMessageIds = data.sentMessageIds || []
    pendingBroadcastCount = sentClawIds.length

    sentClawIds.forEach((clawId, i) => {
      const subMsgId = sentMessageIds[i] || `${messageId}_${clawId}`
      messages.value.push({
        messageId: subMsgId,
        role: 'assistant',
        content: '',
        loading: true,
        clawId   // 标记来源设备
      })
      pendingContent.set(subMsgId, '')
    })

    if (sentClawIds.length === 0) {
      loading.value = false
    }
  } catch (e) {
    console.error('[aiService] 广播失败：', e)
    messages.value.push({ messageId, role: 'assistant', content: '网络错误，请重试', loading: false })
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
