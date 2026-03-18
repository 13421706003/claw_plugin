<template>
  <view class="chat-page">
    <!-- 主内容区域 -->
    <view class="main-content">
      <!-- 顶部导航栏 -->
      <view class="header">
        <view class="header-left">
          <view class="header-logo">
            <image src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
              mode="aspectFit" style="width: 32rpx; height: 32rpx;" />
          </view>
          <text class="header-title">OPENHSD</text>
        </view>
        <view class="header-right">
          <picker v-model="selectedClawIndex" :range="clawList" range-key="clawId"
            :class="{ 'device-select-bar': true, 'active': activeDropdown === 'device' }" style="margin-left: 16rpx;"
            @click="activeDropdown = 'device'; $event.stopPropagation()" @change="handleClawChange">
            <view class="device-select-input">
              <text class="device-select-placeholder">{{ clawList[selectedClawIndex]?.clawId || '选择设备' }}</text>
              <image src="/static/down.png" mode="aspectFit" style="width: 34rpx; height: 34rpx; color: #666;" />
            </view>
          </picker>

          <view class="ws-badge" :class="isConnected ? 'connected' : 'disconnected'">
            <view class="ws-dot"></view>
            <text class="ws-text">{{ isConnected ? '已连接' : '离线' }}</text>
          </view>
          <view class="user-btn" @tap="showUserPanel = !showUserPanel">
            <view class="user-avatar">
              <text class="user-avatar-text">{{ userInitial }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 用户面板 -->
      <view v-if="showUserPanel" class="panel-mask" @tap="showUserPanel = false"></view>
      <scroll-view v-if="showUserPanel" class="user-panel" scroll-y>
        <view class="panel-user-head">
          <view class="panel-avatar">
            <text class="panel-avatar-text">{{ userInitial }}</text>
          </view>
          <view class="panel-user-info" v-if="currentUser">
            <text class="panel-username">{{ currentUser.username }}</text>
            <text class="panel-userid">ID: {{ currentUser.userId }}</text>
          </view>
        </view>
        <view class="panel-section">
          <text class="panel-section-title">TOKEN</text>
          <view class="token-box">
            <text class="token-text" selectable>{{ currentToken }}</text>
          </view>
          <view class="copy-btn" @tap="copyToken">
            <text class="copy-btn-text">{{ tokenCopied ? '已复制!' : '复制' }}</text>
          </view>
        </view>
        <view class="token-tip">
          <text class="token-tip-text">将此 Token 填入插件 cj.config.json → cloud.token</text>
        </view>
        <view class="panel-section">
          <view class="panel-section-header">
            <text class="panel-section-title">在线设备</text>
            <text class="panel-refresh" @tap="fetchClawStatus">{{ loadingStatus ? '刷新中' : '刷新' }}</text>
          </view>
          <view v-for="claw in clawList" :key="claw.clawId" class="claw-item">
            <view class="claw-item-head">
              <view class="claw-online-dot"></view>
              <text class="claw-id">{{ claw.clawId }}</text>
            </view>
            <text class="claw-heartbeat">{{ formatHeartbeat(claw.lastHeartbeat) }}</text>
          </view>
        </view>
        <view class="logout-btn" @tap="onLogout">
          <text class="logout-btn-text">退出登录</text>
        </view>
      </scroll-view>

      <!-- 聊天标题区 -->
      <view class="chat-header">
        <view class="chat-header-left">
          <text class="chat-title">聊天</text>
          <text class="chat-desc">用于快速干预的直接网关聊天会话。</text>
        </view>
        <view class="chat-header-right">
          <picker v-model="selectedIndex" :range="sessionOptions" range-key="label"
            :class="{ 'session-picker': true, 'active': activeDropdown === 'session' }"
            @change="(e) => { selectedIndex = e.detail.value; console.log('🔄 Picker change：索引=', e.detail.value); }"
            @click="activeDropdown = 'session'; $event.stopPropagation()">
            <view class="picker-input">
              <text class="picker-text">
                {{ sessionOptions[selectedIndex].label }}
              </text>
              <image src="/static/down.png" mode="aspectFit" style="width: 34rpx; height: 34rpx; color: #999;" />
            </view>
          </picker>

          <!-- 刷新按钮 -->
        <view class="refresh-btn" @tap="refreshChat">
            <image src="/static/refresh.png" mode="aspectFit" style="width: 50rpx; height: 50rpx;color:#f0f0f0" />
          </view>
        </view>
      </view>


      <!-- 聊天区域 -->
      <scroll-view class="chat-scroll" scroll-y :scroll-top="scrollTop" :scroll-with-animation="true"
        show-scrollbar="false">
        <!-- 欢迎界面 -->
        <view v-if="messages.length === 0" class="welcome-wrap">
          <view class="welcome-header">
            <view class="welcome-icon-wrap">
              <image
                src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*s5sNRo5LjfQAAAAAAAAAAAAADgCCAQ/fmt.webp"
                mode="aspectFit" class="welcome-icon" />
            </view>
            <view class="welcome-text-container">
              <text class="welcome-title">你好，我是 AI 助手</text>
              <text class="welcome-desc">基于 Ant Design X Vue 构建的智能对话界面，为您提供更好的 AI 交互体验~</text>
            </view>
          </view>
          <view class="prompt-cards">
            <!-- 只保留热门话题分组（渐变背景） -->
            <view class="prompt-group-wrap">
              <view class="prompt-group">
                <text class="prompt-group-title">热门话题</text>
                <!-- 热门话题问题（透明背景） -->
                <view class="prompt-question" @tap="onPromptTap('这个项目是做什么的？')">
                  <text class="prompt-question-text">这个项目是做什么的？</text>
                </view>
                <view class="prompt-question" @tap="onPromptTap('如何使用这个对话界面？')">
                  <text class="prompt-question-text">如何使用这个对话界面？</text>
                </view>
                <view class="prompt-question" @tap="onPromptTap('有哪些技术特点？')">
                  <text class="prompt-question-text">有哪些技术特点？</text>
                </view>
                <view class="prompt-question" @tap="onPromptTap('如何快速开始开发？')">
                  <text class="prompt-question-text">如何快速开始开发？</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 消息列表 -->
        <view v-else class="msg-list">
          <view v-for="(msg, idx) in messages" :key="msg.messageId || idx" class="msg-row"
            :class="msg.role === 'user' ? 'msg-row-user' : 'msg-row-assistant'">
            <view v-if="msg.role === 'assistant'" class="msg-avatar assistant-avatar">
              <image
                src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
                mode="aspectFit" style="width: 28rpx; height: 28rpx;" />
            </view>
            <view class="msg-bubble-wrap" :class="msg.role === 'user' ? 'bubble-wrap-user' : 'bubble-wrap-assistant'">
              <view v-if="msg.role === 'user' && msg.attachments && msg.attachments.length > 0" class="attachment-row">
                <image v-for="(att, ai) in msg.attachments" :key="ai" :src="att.base64" mode="aspectFill"
                  class="attachment-img" @tap="previewImage(att.base64)" />
              </view>
              <view class="msg-bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-assistant'">
                <text v-if="msg.role === 'user'" class="bubble-text-user" selectable>{{ msg.content }}</text>
                <view v-else-if="msg.loading && !msg.content" class="typing-dots">
                  <view class="dot"></view>
                  <view class="dot"></view>
                  <view class="dot"></view>
                </view>
                <view v-else class="bubble-md">
                  <rich-text :nodes="renderMarkdown(msg.content)"></rich-text>
                </view>
              </view>
            </view>
            <view v-if="msg.role === 'user'" class="msg-avatar user-avatar-msg">
              <text class="user-avatar-msg-text">{{ userInitial }}</text>
            </view>
          </view>
          <view style="height: 24rpx;"></view>
        </view>
      </scroll-view>

      <!-- 输入区域 -->
      <view class="input-area" :style="{ paddingBottom: safeBottom + 'px' }">
        <!-- 快捷功能栏 -->
        <view class="quick-bar">
          <view v-for="item in quickItems" :key="item.key" class="quick-item" @tap="onQuickItemTap(item.key)"
            hover-class="quick-item-active">
            <text class="quick-item-text">{{ item.text }}</text>
          </view>
        </view>
        <!-- 输入框容器 - 包含预览和输入框 -->
        <view class="input-container" :class="{ 'has-attachments': attachments.length > 0, 'focused': inputFocused }">
          <!-- 附件预览网格 - 在顶部 -->
          <view v-if="attachments.length > 0" class="attachment-preview-container">
            <view class="attachment-preview-list">
              <view v-for="att in attachments" :key="att.uid" class="att-item">
                <image :src="att.base64" mode="aspectFill" class="att-thumb" @tap="previewImage(att.base64)" />
                <view class="att-remove" @tap="removeAttachment(att.uid)">
                  <text class="att-remove-text">×</text>
                </view>
              </view>
            </view>
          </view>
          <!-- 输入框行 -->
          <view class="input-row" :class="{ 'active': inputRowActive }">
            <view class="input-action-btn" @tap="chooseImage">
              <image src="/static/document.png" mode="aspectFit" style="width: 30rpx; height: 30rpx;" />
            </view>
            <textarea class="input-box" v-model="inputValue" placeholder="请输入消息内容..."
              placeholder-class="input-placeholder" :disabled="loading" auto-height :max-height="120" confirm-type="send"
              @confirm="onSend" @focus="onInputFocus" @blur="onInputBlur" />
            <view class="input-send-btn" @tap="onSend">
              <image src="/static/arrow-top.png" mode="aspectFit" style="width: 32rpx; height: 32rpx; color: #fff;" />
            </view>
          </view>
        </view>
        <view class="input-buttons">
          <view class="new-session-btn" @tap="onNewSession">
            <text class="new-session-text">New session</text>
          </view>
          <view class="send-btn" :class="{ 'send-btn-active': inputValue.trim() || attachments.length > 0 }"
            @tap="onSend">
            <view v-if="loading" class="send-spinner"></view>
            <text v-else class="send-icon">Send</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick, onUnmounted  } from 'vue'
import { useUserStore, getUserToken, getUserData } from '../../store/user.js'
import { getClawStatus } from '../../api/claw.js'
import {
  loading, messages, currentClawId,
  isConnected, connect, disconnect,
  send, selectClaw, clearHistory, loadHistory
} from '../../api/aiService.js'

const userStore = useUserStore()

const showUserPanel = ref(false)
const tokenCopied = ref(false)
const clawList = ref([])
const loadingStatus = ref(false)
const selectedClawIndex = ref(0)
const deviceSearchQuery = ref('')
const filteredClawList = ref([])
const inputValue = ref('')
const attachments = ref([])
const scrollTop = ref(0)
const safeBottom = ref(0)
const inputFocused = ref(false)
const inputRowActive = ref(false);
const sessionOptions = ref([
  { value: 'main', label: 'Main Session' },
  { value: 'session2', label: 'Session 2' },
])
const selectedIndex = ref(0)
const currentSession = ref(sessionOptions.value[selectedIndex.value].value)
const activeDropdown = ref(null)

// 解包 computed ref，确保模板中能正确访问
const currentUser  = computed(() => getUserData())
const currentToken = computed(() => getUserToken())

const userInitial = computed(() => {
  const user = currentUser.value
  if (!user || !user.username) return 'U'
  return user.username.charAt(0).toUpperCase()
})

const quickPrompts = [
  { key: '1', text: '这个项目是什么？' },
  { key: '2', text: '如何使用？' },
  { key: '3', text: '有哪些功能？' },
  { key: '4', text: '技术特点？' },
]
const quickItems = [
  { key: '1', text: '项目介绍' },
  { key: '2', text: '功能演示' },
  { key: '3', text: '使用指南' },
  { key: '4', text: '技术支持' },
]
watch(
  selectedIndex,
  async (newIndex, oldIndex) => {
    currentSession.value = sessionOptions.value[newIndex].value
    // 切换 session：清空消息，Main Session 重新加载历史，其他 session 从空白开始
    if (oldIndex !== undefined) {
      if (currentSession.value === 'main' && currentClawId.value) {
        await loadHistory(currentClawId.value)
      } else {
        messages.value = []
      }
    }
  },
  { immediate: true, deep: false }
)
// 点击外部关闭下拉框激活状态
const handleClickOutside = (event) => {
  const pickers = document.querySelectorAll('.device-select-bar, .session-picker')
  let clickedInside = false

  pickers.forEach(picker => {
    if (picker.contains(event.target)) {
      clickedInside = true
    }
  })

  if (!clickedInside) {
    activeDropdown.value = null
  }
}
const refreshChat = async () => {
  if (!currentClawId.value) return
  if (currentSession.value === 'main') {
    await loadHistory(currentClawId.value)
  } else {
    messages.value = []
  }
  uni.showToast({ title: '已刷新', icon: 'none', duration: 800 })
}
onMounted(async () => {
  const sys = uni.getSystemInfoSync()
  document.addEventListener('click', handleClickOutside)
  safeBottom.value = sys.safeAreaInsets?.bottom || 0
  // 确保登录后连接，把 userId 直接传入以兼容"不记住登录"的情况
  if (currentToken.value) {
    connect(currentUser.value?.userId)
    await fetchClawStatus()
    // 设备加载完后自动选中并回显第一个
    if (clawList.value.length > 0) {
      selectedClawIndex.value = 0
      onClawChange(clawList.value[0].clawId)
    }
  }
})
onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})
// 设备选择处理函数
const handleClawChange = (e) => {
  selectedClawIndex.value = e.detail.value
  const clawId = clawList.value[e.detail.value]?.clawId
  if (clawId) {
    onClawChange(clawId)
  }
}

watch(messages, () => {
  nextTick(() => { scrollTop.value = 999999 })
}, { deep: true })

const fetchClawStatus = async () => {
  const userId = currentUser.value?.userId
  if (!userId || !currentToken.value) return
  loadingStatus.value = true
  try {
    const data = await getClawStatus(userId, currentToken.value)
    clawList.value = data.clawList || []
    filteredClawList.value = clawList.value
  } catch (e) {
    console.error('[Chat] 获取设备失败：', e)
  } finally {
    loadingStatus.value = false
  }
}

const onClawChange = async (clawId) => {
  await selectClaw(clawId, currentToken.value)
}

const formatHeartbeat = (ts) => {
  if (!ts) return '未知'
  const diff = Date.now() - ts
  if (diff < 60000) return `${Math.floor(diff / 1000)}s前`
  return `${Math.floor(diff / 60000)}min前`
}

const filterDevices = () => {
  if (!deviceSearchQuery.value) {
    filteredClawList.value = clawList.value
    return
  }
  const query = deviceSearchQuery.value.toLowerCase()
  filteredClawList.value = clawList.value.filter(claw =>
    claw.clawId.toLowerCase().includes(query) ||
    (claw.name && claw.name.toLowerCase().includes(query))
  )
}

const copyToken = () => {
  const tokenStr = currentToken.value
  uni.setClipboardData({
    data: tokenStr,
    success: () => {
      tokenCopied.value = true
      setTimeout(() => { tokenCopied.value = false }, 1500)
    }
  })
}

const onLogout = () => {
  disconnect()
  userStore.logout()
  uni.reLaunch({ url: '/pages/login/login' })
}

const onNewSession = async () => {
  await clearHistory(currentToken.value)
  uni.showToast({ title: '已清空', icon: 'none', duration: 1200 })
}

const chooseImage = () => {
  const sysInfo = uni.getSystemInfoSync()
  const platform = sysInfo.platform?.toLowerCase() || ''
  const isH5 = platform === 'web' || platform === 'h5' || typeof document !== 'undefined'
  
  console.log('[Chat] 平台检测:', { platform, isH5 })
  
  // 定义 H5 文件选择函数
  const chooseImageFromH5 = () => {
    const input = document.createElement('input')
    input.type = 'file'
    input.accept = 'image/*'
    input.multiple = true
    
    input.onchange = (e) => {
      const files = e.target.files
      if (!files || files.length === 0) return
      
      for (let i = 0; i < Math.min(files.length, 3); i++) {
        const file = files[i]
        const reader = new FileReader()
        
        reader.onload = (event) => {
          const ext = file.name.split('.').pop()?.toLowerCase() || 'jpg'
          const mime = file.type || (ext === 'png' ? 'image/png' : 'image/jpeg')
          const base64 = event.target.result
          
          // 计算文件大小
          const sizeInKB = (file.size / 1024).toFixed(1)
          
          attachments.value.push({
            uid: Date.now() + '-' + i,
            name: file.name || `image_${Date.now()}.${ext}`,
            size: sizeInKB,
            base64: base64
          })
          console.log('[Chat] H5 文件上传成功:', file.name, `${sizeInKB} KB`)
        }
        
        reader.onerror = () => {
          console.error('[Chat] 文件读取失败:', file.name)
          uni.showToast({ title: '文件读取失败', icon: 'none', duration: 1000 })
        }
        
        reader.readAsDataURL(file)
      }
    }
    
    input.click()
  }
  
  // 定义原生平台文件选择函数
  const chooseImageFromNative = () => {
    uni.chooseImage({
      count: 3,
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        console.log('[Chat] uni.chooseImage 成功，返回:', res)
        res.tempFilePaths?.forEach((path, i) => {
          // 尝试使用 FileSystemManager
          if (uni.getFileSystemManager && typeof uni.getFileSystemManager === 'function') {
            try {
              uni.getFileSystemManager().readFile({
                filePath: path,
                encoding: 'base64',
                success: (r) => {
                  const ext = path.split('.').pop()?.toLowerCase() || 'jpg'
                  const mime = ext === 'png' ? 'image/png' : 'image/jpeg'
                  // 计算文件大小（base64转换后的大小估算）
                  const sizeInKB = (r.data.length / 1024).toFixed(1)
                  attachments.value.push({
                    uid: Date.now() + '-' + i,
                    name: `image_${Date.now()}.${ext}`,
                    size: sizeInKB,
                    base64: `data:${mime};base64,${r.data}`
                  })
                  console.log('[Chat] 原生平台文件读取成功:', sizeInKB, 'KB')
                },
                fail: (err) => {
                  console.error('[Chat] FileSystemManager 读取失败:', err)
                  uni.showToast({ title: '文件读取失败', icon: 'none', duration: 1000 })
                }
              })
            } catch (e) {
              console.error('[Chat] FileSystemManager 异常:', e)
              uni.showToast({ title: '文件读取异常', icon: 'none', duration: 1000 })
            }
          } else {
            console.warn('[Chat] getFileSystemManager 不可用，尝试备降到 FileReader')
            // 备降方案：如果是 H5 也支持 FileReader，则尝试使用
            if (typeof FileReader !== 'undefined' && typeof fetch !== 'undefined') {
              fetch(`file://${path}`)
                .then(res => res.blob())
                .then(blob => {
                  const reader = new FileReader()
                  reader.onload = (e) => {
                    const ext = path.split('.').pop()?.toLowerCase() || 'jpg'
                    const mime = ext === 'png' ? 'image/png' : 'image/jpeg'
                    attachments.value.push({
                      uid: Date.now() + '-' + i,
                      name: `image_${Date.now()}.${ext}`,
                      base64: e.target.result
                    })
                  }
                  reader.readAsDataURL(blob)
                })
                .catch(() => {
                  uni.showToast({ title: '该平台不支持文件读取', icon: 'none', duration: 1000 })
                })
            } else {
              uni.showToast({ title: '该平台不支持文件读取', icon: 'none', duration: 1000 })
            }
          }
        })
      },
      fail: (err) => {
        console.error('[Chat] uni.chooseImage 失败:', err)
      }
    })
  }
  
  // 根据平台选择对应的处理方式
  if (isH5) {
    console.log('[Chat] 使用 H5 文件选择')
    chooseImageFromH5()
  } else {
    console.log('[Chat] 使用原生平台文件选择')
    chooseImageFromNative()
  }
}

const removeAttachment = (uid) => {
  attachments.value = attachments.value.filter(a => a.uid !== uid)
}

const previewImage = (src) => {
  uni.previewImage({ urls: [src], current: src })
}

const onSend = async () => {
  const text = inputValue.value.trim()
  if (!text && attachments.value.length === 0) return
  if (loading.value) {
    uni.showToast({ title: '请等待回复', icon: 'none' })
    return
  }
  const atts = [...attachments.value]
  inputValue.value = ''
  attachments.value = []
  await send(text, atts, currentToken.value)
}

const onPromptTap = (text) => {
  inputValue.value = text
  onSend()
}

const onQuickItemTap = (key) => {
  // 处理快捷功能点击
  switch (key) {
    case '1':
      inputValue.value = '项目介绍'
      break
    case '2':
      inputValue.value = '功能演示'
      break
    case '3':
      inputValue.value = '使用指南'
      break
    case '4':
      inputValue.value = '技术支持'
      break
  }
  onSend()
}

const onInputFocus = () => {
  inputFocused.value = true
  inputRowActive.value = true
  console.log('inputRowActive:', inputRowActive.value); // 应输出true
}

const onInputBlur = () => {
  inputFocused.value = false
  inputRowActive.value = false
  console.log('inputRowActive:', inputRowActive.value); // 应输出true
}

const renderMarkdown = (content) => {
  if (!content) return ''
  return content
    .replace(/```[\w]*\n?([\s\S]*?)```/g, '<pre style="background:#f5f5f5;padding:16rpx;border-radius:8rpx;margin:8rpx 0;overflow:auto"><code style="font-size:22rpx;font-family:monospace;color:#333">$1</code></pre>')
    .replace(/`([^`]+)`/g, '<text style="background:#f0f0f0;padding:2rpx 6rpx;border-radius:4rpx;font-size:0.9em;color:#d56161">$1</text>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/^### (.+)$/gm, '<text style="font-weight:600;color:#333;padding-left:8rpx;border-left:3px solid #ccc;display:block;margin:8rpx 0">$1</text>')
    .replace(/^## (.+)$/gm, '<text style="font-weight:600;font-size:1.1em;color:#1a1a1a;display:block;margin:10rpx 0">$1</text>')
    .replace(/^# (.+)$/gm, '<text style="font-weight:700;font-size:1.2em;color:#1a1a1a;display:block;margin:12rpx 0">$1</text>')
    .replace(/^[*\-] (.+)$/gm, '<text style="padding-left:20rpx;display:block;margin:4rpx 0">• $1</text>')
    .replace(/\n/g, '<br/>')
}
</script>

<style scoped>
.chat-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
}

/* 主内容区域 */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 0;
  overflow: hidden;
}

/* 顶部导航栏 */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  margin-top: 8rpx;
  height: 88rpx;
  background: #ffffff;
  flex-shrink: 0;
  padding-top: var(--status-bar-height, 0px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.header-logo {
  width: 48rpx;
  height: 48rpx;
  border-radius: 12rpx;
  background: #f2f2f7;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-title {
  font-size: 30rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10rpx;
  flex-shrink: 0;
  overflow: hidden;
}

.ws-badge {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 12rpx;
  border-radius: 20rpx;
  background: rgba(0, 0, 0, 0.04);
  white-space: nowrap;
  flex-shrink: 0;
}

.ws-badge.connected {
  background: rgba(82, 196, 26, 0.1);
}

.ws-badge.disconnected {
  background: rgba(255, 77, 79, 0.08);
}

.ws-badge.connected .ws-dot {
  background: #52c41a;
  box-shadow: 0 0 0 3rpx rgba(82, 196, 26, 0.25);
}

.ws-badge.disconnected .ws-dot {
  background: #ff4d4f;
}

.ws-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.ws-text {
  font-size: 20rpx;
  color: rgba(0, 0, 0, 0.6);
  white-space: nowrap;
}

.user-btn {
  padding: 6rpx;
}

.user-avatar {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4d4f, #d9363e);
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar-text {
  font-size: 20rpx;
  font-weight: 700;
  color: #fff;
}

/* 聊天标题区 */
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  /* 垂直居中 */
  padding: 20rpx 24rpx 0;
  margin-bottom: 24rpx;
}

/* 左侧标题区域 */
.chat-header-left {
  display: flex;
  flex-direction: column;
}

/* 聊天标题 */
.chat-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 4rpx;
}

/* 聊天描述 */
.chat-desc {
  font-size: 20rpx;
  color: #999;
}

/* 右侧操作区域 */
.chat-header-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
  justify-content: center;
  /* 水平居中 */
  padding-left: 16rpx;
  /* 增加左侧内边距，让居中更明显 */
  width: auto;
  /* 自适应宽度，避免占满右侧 */
}

/* 会话选择器容器 */
.session-picker {
  width: 180rpx;
  border: 2rpx solid #e5e7eb;
  border-radius: 10rpx;
  padding: 6rpx 10rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.session-picker.active {
  border-color: #1677ff;
  box-shadow: 0 0 0 2rpx rgba(22, 119, 255, 0.1);
}

/* 选择器输入框 */
.picker-input {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4rpx;
}

/* 选择器文字 */
.picker-text {
  font-size: 18rpx;
  color: #333;
}

/* 刷新按钮 */
.refresh-btn {
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

/* 用户面板 */
.panel-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgba(0, 0, 0, 0.15);
}

.user-panel {
  position: fixed;
  top: calc(88rpx + var(--status-bar-height, 0px));
  right: 16rpx;
  width: 480rpx;
  max-height: 70vh;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.12);
  z-index: 101;
}

.panel-user-head {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx 24rpx 20rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.panel-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4d4f, #d9363e);
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-avatar-text {
  font-size: 24rpx;
  font-weight: 700;
  color: #fff;
}

.panel-user-info {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.panel-username {
  font-size: 26rpx;
  font-weight: 600;
  color: #1a1a1a;
}

.panel-userid {
  font-size: 20rpx;
  color: #999;
}

.panel-section {
  padding: 20rpx 24rpx 0;
}

.panel-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.panel-section-title {
  font-size: 18rpx;
  font-weight: 600;
  color: #999;
  letter-spacing: 1rpx;
  display: block;
  margin-bottom: 12rpx;
}

.panel-refresh {
  font-size: 20rpx;
  color: #1677ff;
  margin-bottom: 12rpx;
}

/* 输入框右侧蓝色圆形按钮 */
.input-send-btn {
  width: 50rpx;
  /* 增大尺寸 */
  height: 50rpx;
  /* 增大尺寸 */
  border-radius: 50%;
  background-color: #1677ff;
  /* 蓝色 */
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 12rpx;
  flex-shrink: 0;
  padding: 6rpx;
  /* 增加内边距 */
}

.token-box {
  background: #f6f6f6;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  margin-bottom: 12rpx;
  max-height: 100rpx;
  overflow: hidden;
}

.token-text {
  font-size: 18rpx;
  font-family: monospace;
  color: #333;
  word-break: break-all;
  line-height: 1.5;
}

.token-tip {
  background: #f0f7ff;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  margin-bottom: 12rpx;
}

.token-tip-text {
  font-size: 18rpx;
  color: #1677ff;
  line-height: 1.5;
}

.copy-btn {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 10rpx;
  padding: 12rpx;
  text-align: center;
  margin-bottom: 20rpx;
}

.copy-btn-text {
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.6);
}

/* 设备选择下拉框 */
.device-search-wrap {
  position: relative;
  margin-bottom: 16rpx;
}

.device-search-input {
  width: 100%;
  height: 64rpx;
  padding: 0 20rpx;
  background: #fafafa;
  border-radius: 12rpx;
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 16rpx;
}

.device-search-placeholder {
  color: rgba(0, 0, 0, 0.3);
  font-size: 22rpx;
}

.device-select {
  padding: 16rpx;
  background: #fafafa;
  border-radius: 12rpx;
  text-align: center;
}


.device-select-placeholder {
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.6);
}

.claw-empty {
  padding: 16rpx;
  background: #fafafa;
  border-radius: 12rpx;
  text-align: center;
  margin-bottom: 16rpx;
}

.claw-empty-text {
  font-size: 22rpx;
  color: #bbb;
}

.claw-item {
  padding: 14rpx 18rpx;
  background: #f8fffe;
  border: 1rpx solid #d9f7be;
  border-radius: 12rpx;
  margin-bottom: 10rpx;
}

.claw-item-head {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-bottom: 4rpx;
}

.claw-online-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: #52c41a;
}

.claw-id {
  font-size: 22rpx;
  font-weight: 600;
  color: #1a1a1a;
}

.claw-heartbeat {
  font-size: 18rpx;
  color: #aaa;
}

.logout-btn {
  margin: 20rpx 24rpx 24rpx;
  padding: 18rpx;
  background: rgba(255, 77, 79, 0.06);
  border-radius: 12rpx;
  text-align: center;
}

.logout-btn-text {
  font-size: 24rpx;
  color: #ff4d4f;
  font-weight: 500;
}

/* 设备选择栏 */
.toolbar {
  display: flex;
  align-items: center;
  padding: 10rpx 20rpx;
  background: #fafafa;
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
  gap: 12rpx;
}

.device-search-wrap {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.device-search-input {
  height: 64rpx;
  padding: 0 20rpx;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 100rpx;
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.85);
  flex: 1;
}

.device-search-placeholder {
  color: rgba(0, 0, 0, 0.3);
  font-size: 22rpx;
}

.device-select-bar {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  border-radius: 10rpx;
}

.device-select-bar.active {
  border: 2rpx solid #1677ff;
  box-shadow: 0 0 0 2rpx rgba(22, 119, 255, 0.1);
}

.device-select-input {
  display: flex;
  gap: 6rpx;
  align-items: center;
  padding: 6rpx 12rpx;
  border-radius: 20rpx;
  border: 1rpx solid rgba(0, 0, 0, 0.1);
  max-width: 220rpx;
  background: rgba(0, 0, 0, 0.03);
}

.device-select-placeholder {
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.65);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160rpx;
}

.device-select-placeholder {
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.6);
}

/* 快捷功能栏 */
.quick-bar {
  display: flex;
  align-items: center;
  padding-bottom: 8rpx;
  flex-shrink: 0;
  gap: 10rpx;
  overflow-x: auto;
}

.quick-item {
  padding: 8rpx 18rpx;
  background-color: transparent;
  border: 1rpx solid rgba(0, 0, 0, 0.12);
  border-radius: 20rpx;
  flex-shrink: 0;
  transition: background-color 0.15s ease;
}

.quick-item-active {
  background-color: #d5d5d5;
  /* 深灰（比原始浅灰深一级） */
  box-shadow: inset 0 2rpx 4rpx rgba(0, 0, 0, 0.1);
  /* 内阴影反馈 */
  transition: background-color 0.2s ease;
  /* 平滑过渡 */
}

.quick-item-text {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.6);
  white-space: nowrap;
}

/* 聊天区域 */
.chat-scroll {
  flex: 1;
  min-height: 0;
}

/* 欢迎界面 */
.welcome-wrap {
  padding: 48rpx 32rpx 32rpx;
}

.welcome-header {
  display: flex;
  align-items: center;
  margin-bottom: 42rpx;
  max-width: 100%;
  /* 防止溢出 */
}

.welcome-icon-wrap {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}

.welcome-icon {
  width: 100rpx;
  height: 100rpx;
}

.welcome-title {
  font-size: 34rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.85);
  margin-bottom: 8rpx;
}

.welcome-text-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  /* 占据剩余空间 */
  min-width: 0;
  /* 防止溢出 */
  margin-left: 16rpx;
  /* 增加与图标间距 */
}

.welcome-desc {
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.6);
  margin-top: 4rpx;
  /* 增加与标题间距 */
}

/* 提示卡片容器 */
.prompt-cards {
  display: grid;
  grid-template-columns: 1fr;
  /* 改为单列布局 */
  gap: 0;
  /* 去掉列间距 */
  width: 100%;
}

/* 分组背景容器（渐变） */
.prompt-group-wrap {
  background: linear-gradient(123deg, #e5f4ff 0%, #efe7ff 100%);
  /* 保持渐变背景 */
  border-radius: 16rpx;
  /* 保持圆角 */
  padding: 30rpx;
  /* 调整内边距 */
  width: 100%;
  /* 占满宽度 */
}

/* 分组容器 */
.prompt-group {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  /* 卡片间距 */
}

/* 分组标题 */
.prompt-group-title {
  font-size: 28rpx;
  font-weight: bold;
  color: #333;
  margin-bottom: 12rpx;
}

/* 热门话题问题（透明背景） */
.prompt-question {
  font-size: 24rpx;
  color: #666;
  cursor: pointer;
  transition: color 0.2s;
  line-height: 1.6;
  background-color: transparent;
  /* 透明背景 */
}

/* 热门话题问题 hover 效果 */
.prompt-question:hover {
  color: #1677ff;
}

/* 设计指南卡片（半透明白色背景） */
.prompt-card {
  display: flex;
  /* 启用Flex布局 */
  flex-direction: column;
  /* 垂直排列子元素 */
  background-color: #ffffffa6;
  /* 半透明白色 */
  border-radius: 12rpx;
  /* 圆角 */
  padding: 16rpx;
  cursor: pointer;
  transition: background-color 0.2s;
  border: none;
  /* 移除边框 */
}


/* 设计指南卡片标题 */
.prompt-card-title {
  font-size: 24rpx;
  font-weight: 500;
  color: #333;
  margin-bottom: 8rpx;
  /* 标题与描述间距 */
}

/* 设计指南卡片描述 */
.prompt-card-desc {
  font-size: 22rpx;
  color: #666;
  line-height: 1.5;
}

/* 设计指南卡片 hover 效果 */
.prompt-card:hover {
  background-color: #ffffffcc;
  /*  hover时更不透明 */
}

/* 设计指南卡片（白色背景圆角） */
.prompt-card {
  background-color: #fff;
  /* 白色背景 */
  border-radius: 12rpx;
  /* 圆角 */
  border: 1rpx solid #eee;
  /* 浅边框 */
  padding: 16rpx;
  cursor: pointer;
  transition: border-color 0.2s;
}

/* 设计指南卡片标题 */
.prompt-card-title {
  font-size: 24rpx;
  font-weight: 500;
  color: #333;
  margin-bottom: 8rpx;
}

/* 设计指南卡片描述 */
.prompt-card-desc {
  font-size: 22rpx;
  color: #666;
  line-height: 1.5;
}

/* 设计指南卡片 hover 效果 */
.prompt-card:hover {
  border-color: #1677ff;
}

/* 消息列表 */
.msg-list {
  padding: 24rpx 20rpx 8rpx;
}

.msg-row {
  display: flex;
  align-items: flex-start;
  gap: 14rpx;
  margin-bottom: 28rpx;
  min-width: 0;
  overflow: hidden;
}

.msg-row-user {
  flex-direction: row-reverse;
}

.msg-row-assistant {
  flex-direction: row;
}

/* 头像 */
.msg-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 4rpx;
}

.assistant-avatar {
  background: #f0f0f5;
  border: 1rpx solid rgba(0, 0, 0, 0.06);
}

.user-avatar-msg {
  background: linear-gradient(135deg, #ff4d4f, #d9363e);
  box-shadow: 0 2rpx 8rpx rgba(255, 77, 79, 0.3);
}

.user-avatar-msg-text {
  font-size: 22rpx;
  font-weight: 700;
  color: #fff;
}

/* 气泡容器 */
.msg-bubble-wrap {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
  gap: 6rpx;
}

.bubble-wrap-user {
  align-items: flex-end;
  max-width: 75%;
}

.bubble-wrap-assistant {
  align-items: flex-start;
  max-width: 85%;
  min-width: 0;
  flex: 1;
}

/* 附件 */
.attachment-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6rpx;
  justify-content: flex-end;
}

.attachment-img {
  width: 120rpx;
  height: 120rpx;
  border-radius: 12rpx;
  object-fit: cover;
}

/* 气泡通用 */
.msg-bubble {
  padding: 18rpx 22rpx;
  border-radius: 20rpx;
  word-break: break-word;
  line-height: 1.6;
}

/* 用户气泡：深色 */
.bubble-user {
  background: #f2f2f7;
  border-bottom-right-radius: 6rpx;
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.15);
}

/* AI 气泡：灰色背景 */
.bubble-assistant {
  background: #f2f2f7;
  border-bottom-left-radius: 6rpx;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.bubble-text-user {
  font-size: 27rpx;
  color: #0a0a0a;
  line-height: 1.6;
}

/* 打字动画 */
.typing-dots {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 4rpx;
}

.dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.25);
  animation: typingDot 1.2s ease-in-out infinite;
}

.dot:nth-child(2) {
  animation-delay: 0.2s;
}

.dot:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typingDot {
  0%, 60%, 100% {
    transform: translateY(0);
    opacity: 0.3;
  }
  30% {
    transform: translateY(-8rpx);
    opacity: 1;
  }
}

/* Markdown 内容 */
.bubble-md {
  font-size: 27rpx;
  color: rgba(0, 0, 0, 0.82);
  line-height: 1.65;
  width: 100%;
  box-sizing: border-box;
  overflow-wrap: break-word;
  word-break: break-word;
  overflow: hidden;
}

/* 输入区域 */
.input-area {
  flex-shrink: 0;
  background: #fff;
  padding: 12rpx 20rpx 20rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  width: 100%;
  transition: all 0.3s ease;
}

/* 输入框容器 - 包含预览和输入框 */
.input-container {
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 2rpx solid rgba(0, 0, 0, 0.15);
  border-radius: 20rpx;
  transition: all 0.3s ease;
  overflow: hidden;
}

.input-container.has-attachments {
  border-color: #1677ff;
}

.input-container.focused {
  border-color: #1677ff;
}

/* 附件预览容器 */
.attachment-preview-container {
  width: 100%;
  background: rgba(0, 0, 0, 0.02);
  padding: 12rpx 12rpx 12rpx 12rpx;
  border-bottom: 1rpx solid rgba(0, 0, 0, 0.06);
}

.attachment-preview-list {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
  width: 100%;
}

/* 附件项 */
.att-item {
  display: flex;
  position: relative;
  flex-shrink: 0;
  animation: attachmentSlideIn 0.3s ease-out;
}

.att-thumb {
  width: 96rpx;
  height: 96rpx;
  border-radius: 8rpx;
  object-fit: cover;
  border: 1rpx solid rgba(0, 0, 0, 0.08);
  transition: all 0.2s ease;
}

.att-thumb:active {
  transform: scale(0.95);
  opacity: 0.8;
}

@keyframes attachmentSlideIn {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

/* 删除按钮 */
.att-remove {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  cursor: pointer;
}

.att-remove:active {
  background: rgba(0, 0, 0, 0.75);
  transform: scale(1.1);
}

.att-remove-text {
  font-size: 20rpx;
  color: #fff;
  line-height: 1;
  font-weight: bold;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 12rpx 12rpx 12rpx 18rpx;
  min-height: 80rpx;
  transition: all 0.2s ease;
}

.input-row.active {
  background: transparent;
}

.input-container.has-attachments .input-row {
  border-top: 1rpx solid rgba(0, 0, 0, 0.06);
  padding-top: 12rpx;
}

.input-action-btn {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.input-action-icon {
  font-size: 32rpx;
  color: rgba(0, 0, 0, 0.5);
  line-height: 1;
}

.input-box {
  flex: 1;
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.85);
  background: transparent;
  min-height: 48rpx;
  max-height: 160rpx;
  line-height: 1.5;
  padding: 8rpx 0;
  border: none;
  outline: none;
}

.input-box:focus {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.2);
}

.input-placeholder {
  color: rgba(0, 0, 0, 0.25);
  line-height: 1.5;
  font-size: 24rpx;
}

.input-buttons {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-left: auto;
  margin-top: 4rpx;
  padding-bottom: 16rpx;
}

.new-session-btn {
  height: 56rpx;
  min-width: 120rpx;
  padding: 0 20rpx;
  border-radius: 20rpx;
  border: 1rpx solid rgba(0, 0, 0, 0.1);
  background: #f5f5f7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.new-session-text {
  font-size: 22rpx;
  color: rgba(0, 0, 0, 0.45);
  white-space: nowrap;
}

.send-btn {
  width: auto;
  min-width: 90rpx;
  height: 56rpx;
  border-radius: 20rpx;
  background: #ff4d4f;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 24rpx;
  flex-shrink: 0;
}

.send-btn-active {
  background: #ff4d4f;
}

.send-icon {
  font-size: 22rpx;
  color: #fff;
  line-height: 1;
  font-weight: 600;
  letter-spacing: 1rpx;
}

.send-spinner {
  width: 28rpx;
  height: 28rpx;
  border: 3rpx solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }

  to {
    transform: rotate(360deg);
  }
}
</style>
