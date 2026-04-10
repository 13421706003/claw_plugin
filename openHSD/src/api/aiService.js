import { ref } from 'vue'
import { useWebSocket, getTabId } from './websocket.js'
import { request, API_BASE } from './request.js'

let msgCounter = 0
const nextMessageId = () => `msg_${Date.now()}_${++msgCounter}`

const loading = ref(false)
const messages = ref([])
const currentClawId = ref(null)

/** 广播模式下，记录有多少台设备还在回复中 */
let pendingBroadcastCount = 0

const pendingContent = new Map()
const pendingThinking = new Map()

/**
 * messageId → clawId 归属映射
 * 收到 WebSocket 回复时，比对 currentClawId 决定是否渲染到当前视图
 * 防止在设备1等待回复时切换到设备2，导致回复消息出现在设备2的列表里
 */
const messageClawMap = new Map()



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
  const { messageId, chunk, result, status, thinking, fullText } = data

  // 处理思考消息增量
  if (type === 'thinking_chunk') {
    const thinkingText = extractText(chunk) || chunk
    if (thinkingText) {
      // 累积追加思考内容
      const prev = pendingThinking.get(messageId) || ''
      const accumulated = prev + thinkingText
      pendingThinking.set(messageId, accumulated)

      // 归属判断
      const belongsTo = messageClawMap.get(messageId)
      if (belongsTo && belongsTo !== currentClawId.value) {
        console.debug(`[aiService] thinking_chunk 跳过渲染（当前设备=${currentClawId.value}，消息归属=${belongsTo}）`)
        return
      }

      // 更新思考内容显示
      updateThinkingMessageStreaming(messageId, accumulated)
    }
  }

  if (type === 'response_chunk') {
    const text = extractText(chunk)
    if (text) {
      // 累积追加，而非替换
      const prev = pendingContent.get(messageId) || ''
      const accumulated = prev + text
      pendingContent.set(messageId, accumulated)

      // 归属判断：该消息不属于当前正在查看的设备，跳过渲染
      const belongsTo = messageClawMap.get(messageId)
      if (belongsTo && belongsTo !== currentClawId.value) {
        console.debug(`[aiService] response_chunk 跳过渲染（当前设备=${currentClawId.value}，消息归属=${belongsTo}）`)
        return
      }

      // streaming=true：已有内容正在流式输出，不显示 loading 转圈
      updateAssistantMessageStreaming(messageId, accumulated)
    }
  }

  if (type === 'response') {
    const text = extractText(result) || pendingContent.get(messageId) || ''
    const thinkingText = thinking || pendingThinking.get(messageId) || ''
    pendingContent.delete(messageId)
    pendingThinking.delete(messageId)

    // 归属判断：不属于当前设备，只清理状态，不渲染到当前消息列表
    // （用户切回该设备时会通过 loadHistory 从数据库拿到完整记录）
    const belongsTo = messageClawMap.get(messageId)
    messageClawMap.delete(messageId)

    if (belongsTo && belongsTo !== currentClawId.value) {
      console.debug(`[aiService] response 跳过渲染（当前设备=${currentClawId.value}，消息归属=${belongsTo}）`)
      // 仍然需要解除 loading 计数，防止广播模式计数器卡住
      if (pendingBroadcastCount > 0) {
        pendingBroadcastCount--
        if (pendingBroadcastCount <= 0) {
          pendingBroadcastCount = 0
          loading.value = false
        }
      } else {
        loading.value = false
      }
      return
    }

    updateAssistantMessage(messageId, text, thinkingText, true)

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

  if (type === 'file_push') {
    handleFilePush(data)
  }
})

const handleFilePush = (data) => {
  const { clawId, fileUrl, fileName, fileType, fileSize } = data
  console.log('[aiService] 收到文件推送：', { clawId, fileUrl, fileName, fileType, fileSize })

  if (currentClawId.value === clawId || currentClawId.value === '__ALL__') {
    const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg']
    const docMimes = [
      'application/pdf',
      'application/msword',
      'application/vnd.openxmlformats-officedocument',
      'application/vnd.ms-',
      'text/plain',
      'text/markdown',
      'text/csv',
      'application/json',
      'application/zip',
      'application/gzip',
      'application/x-zip',
      'application/x-gzip'
    ]
    
    const ext = (fileName || '').split('.').pop().split('?')[0].toLowerCase()
    const mimeType = (fileType || '').toLowerCase()
    
    const isImageByMime = mimeType.startsWith('image/')
    const isImageByExt = imageExts.includes(ext)
    const isDocumentByMime = docMimes.some(m => mimeType.includes(m))
    const isDocumentByExt = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'csv', 'json', 'zip', 'gz'].includes(ext)
    
    const isImage = isImageByMime || (isImageByExt && !isDocumentByMime && !isDocumentByExt)
    
    console.log('[aiService] 文件类型判断：', { ext, mimeType, isImageByMime, isImageByExt, isDocumentByMime, isDocumentByExt, isImage })

    if (isImage) {
      messages.value.push({
        messageId: `file_${Date.now()}`,
        role: 'assistant',
        clawId,
        content: '',
        attachments: [{
          uid: `push_${Date.now()}`,
          name: fileName,
          type: fileType || `image/${ext === 'jpg' ? 'jpeg' : ext}`,
          size: fileSize || 0,
          url: fileUrl,
          base64: fileUrl,
        }],
        loading: false
      })
    } else {
      messages.value.push({
        messageId: `file_${Date.now()}`,
        role: 'assistant',
        clawId,
        content: '',
        filePush: { url: fileUrl, name: fileName, type: fileType, size: fileSize },
        loading: false
      })
    }
  } else {
    console.log(`[aiService] 设备 ${clawId} 已回复`)
  }
}

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

const updateAssistantMessage = (messageId, content, thinking, isFinal) => {
  const idx = messages.value.findIndex(m => m.messageId === messageId && m.role === 'assistant')
  if (idx >= 0) {
    messages.value[idx].content = content
    messages.value[idx].thinking = thinking || ''
    messages.value[idx].loading = !isFinal
    messages.value[idx].streaming = false
    messages.value[idx].thinkingStreaming = false
  } else {
    messages.value.push({
      messageId,
      role: 'assistant',
      content,
      thinking: thinking || '',
      loading: !isFinal,
      streaming: false,
      thinkingStreaming: false
    })
  }
}

/**
 * 流式 chunk 到达时更新消息：
 * - loading=false：不显示转圈动画（气泡已有内容在增量显示）
 * - streaming=true：标记正在流式输出（用于显示闪烁光标）
 */
const updateAssistantMessageStreaming = (messageId, content) => {
  const idx = messages.value.findIndex(m => m.messageId === messageId && m.role === 'assistant')
  if (idx >= 0) {
    messages.value[idx].content = content
    messages.value[idx].loading = false
    messages.value[idx].streaming = true
  } else {
    messages.value.push({
      messageId,
      role: 'assistant',
      content,
      loading: false,
      streaming: true
    })
  }
}

/**
 * 思考内容流式更新：
 * - thinkingStreaming=true：标记思考内容正在流式输出
 */
const updateThinkingMessageStreaming = (messageId, thinking) => {
  const idx = messages.value.findIndex(m => m.messageId === messageId && m.role === 'assistant')
  if (idx >= 0) {
    messages.value[idx].thinking = thinking
    messages.value[idx].thinkingStreaming = true
  } else {
    messages.value.push({
      messageId,
      role: 'assistant',
      content: '',
      thinking,
      loading: true,
      streaming: false,
      thinkingStreaming: true
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
    const res = await request(`/messages?userId=${userId}&clawId=${clawId}`)
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
    await request(`/messages?userId=${userId}&clawId=${currentClawId.value}`, {
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
  lastMessageId = messageId

  messages.value.push({ messageId, role: 'user', content, attachments })
  messages.value.push({ messageId, role: 'assistant', content: '', thinking: '', loading: true, streaming: false, thinkingStreaming: false })
  pendingContent.set(messageId, '')
  pendingThinking.set(messageId, '')
  messageClawMap.set(messageId, currentClawId.value)

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
    const res = await request('/claw/send', {
      method: 'POST',
      body: JSON.stringify({
        userId,
        messageId,
        content,
        clawId: currentClawId.value,
        tabId: getTabId(),
        attachments: formattedAttachments
      })
    })

    const data = await res.json()
    if (!data.success) {
      messageClawMap.delete(messageId)
      updateAssistantMessage(messageId, data.message || '发送失败', true)
      loading.value = false
    }
  } catch (e) {
    console.error('[aiService] 发送失败：', e)
    messageClawMap.delete(messageId)
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
    const res = await request('/claw/broadcast', {
      method: 'POST',
      body: JSON.stringify({
        userId,
        messageId,
        content,
        tabId: getTabId(),
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
        thinking: '',
        loading: true,
        streaming: false,
        thinkingStreaming: false,
        clawId   // 标记来源设备
      })
      pendingContent.set(subMsgId, '')
      pendingThinking.set(subMsgId, '')
      // 广播模式：子消息归属 '__ALL__'，确保广播视图下始终渲染
      messageClawMap.set(subMsgId, '__ALL__')
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

const clearMessages = () => {
  messages.value = []
  currentClawId.value = null
}

let lastMessageId = null

const setLastMessageId = (id) => {
  lastMessageId = id
}

const abortMessage = async () => {
  const userId = getStoredUserId()
  if (!userId || !lastMessageId) {
    console.warn('[aiService] 无法中断：缺少 userId 或 messageId')
    return false
  }

  try {
    const res = await request('/claw/abort', {
      method: 'POST',
      body: JSON.stringify({
        userId,
        messageId: lastMessageId,
        clawId: currentClawId.value === '__ALL__' ? null : currentClawId.value
      })
    })

    const data = await res.json()
    if (data.success) {
      console.log('[aiService] 中断指令已发送')
      pendingContent.delete(lastMessageId)
      messageClawMap.delete(lastMessageId)
      loading.value = false
      pendingBroadcastCount = 0
      return true
    } else {
      console.warn('[aiService] 中断失败：', data.message)
      return false
    }
  } catch (e) {
    console.error('[aiService] 中断请求失败：', e)
    return false
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
  disconnect,
  clearMessages,
  abortMessage,
  setLastMessageId
}
