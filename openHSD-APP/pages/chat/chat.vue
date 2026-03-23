<template>
  <view class="chat-page">
    <!-- 主内容区域 -->
    <view class="main-content">
      <!-- 顶栏：品牌 + 连接状态 + 账号（与 Web 端一致的浅色导航） -->
      <view class="top-bar">
        <view class="top-bar-left">
          <view class="top-logo">
            <image
              src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
              mode="aspectFit"
              class="top-logo-img"
            />
          </view>
          <view class="top-brand">
            <text class="top-brand-name">OPENHSD</text>
            <text class="top-brand-sub">网关聊天</text>
          </view>
        </view>
        <view class="top-bar-right">
          <view class="ws-badge" :class="isConnected ? 'connected' : 'disconnected'">
            <view class="ws-dot"></view>
            <text class="ws-text">{{ isConnected ? '已连接' : '离线' }}</text>
          </view>
          <view class="user-btn" @tap.stop="showUserPanel = !showUserPanel">
            <view class="user-avatar">
              <text class="user-avatar-text">{{ userInitial }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 第二行：设备、会话、刷新、新对话（移动端单行可横向滚动） -->
      <scroll-view class="tool-scroll" scroll-x show-scrollbar="false" enable-flex>
        <view class="tool-row">
          <picker
            v-if="clawList.length > 0"
            :value="selectedClawIndex"
            :range="clawList"
            range-key="clawId"
            :class="['tool-picker-wrap', { active: activeDropdown === 'device' }]"
            @tap="activeDropdown = 'device'"
            @change="handleClawChange"
          >
            <view class="tool-chip tool-chip--device">
              <text class="tool-chip-label">{{ clawList[selectedClawIndex]?.clawId || '选择设备' }}</text>
              <image src="/static/down.png" mode="aspectFit" class="tool-caret" />
            </view>
          </picker>
          <view v-else class="tool-chip tool-chip--device tool-chip--disabled">
            <text class="tool-chip-label">暂无在线设备</text>
          </view>

          <view class="tool-row-trail">
            <picker
              :value="selectedIndex"
              :range="sessionOptions"
              range-key="label"
              :class="['tool-picker-wrap', { active: activeDropdown === 'session' }]"
              @tap="activeDropdown = 'session'"
              @change="handleSessionChange"
            >
              <view class="tool-chip tool-chip--session">
                <text class="tool-chip-label">{{ sessionOptions[selectedIndex]?.label }}</text>
                <image src="/static/down.png" mode="aspectFit" class="tool-caret" />
              </view>
            </picker>

            <view class="tool-icon-btn" @tap.stop="refreshChat">
              <image src="/static/refresh.png" mode="aspectFit" class="tool-icon-img" />
            </view>
            <view class="tool-icon-btn" @tap.stop="onNewSession">
              <MessageSquarePlus :size="21" :stroke-width="1.75" :color="TOOL_ICON_COLOR" />
            </view>
          </view>
        </view>
      </scroll-view>

      <!-- 用户面板 -->
      <transition name="userPanelMask">
        <view v-if="showUserPanel" class="panel-mask" @tap="showUserPanel = false"></view>
      </transition>
      <transition name="userPanelPop">
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
      </transition>

      <!-- 聊天区域 -->
      <scroll-view
        class="chat-scroll"
        scroll-y
        :scroll-top="scrollTop"
        :scroll-with-animation="true"
        show-scrollbar="false"
        refresher-enabled
        :refresher-triggered="chatRefresherTriggered"
        refresher-default-style="black"
        @refresherrefresh="onChatRefresherRefresh"
      >
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
              <text class="welcome-desc">网关直连对话，与 Web 端同一套风格，移动端已为你简化布局。</text>
            </view>
          </view>
          <view class="prompt-cards">
            <view class="prompt-group-wrap">
              <view class="prompt-group">
                <text class="prompt-group-title">试试这样问</text>
                <view class="prompt-question" @tap="onPromptTap('这个项目是做什么的？')">
                  <text class="prompt-question-text">项目是做什么的？</text>
                </view>
                <view class="prompt-question" @tap="onPromptTap('如何使用这个对话界面？')">
                  <text class="prompt-question-text">怎么用这个聊天？</text>
                </view>
                <view class="prompt-question" @tap="onPromptTap('有哪些技术特点？')">
                  <text class="prompt-question-text">有哪些技术特点？</text>
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
           </view>
          <view style="height: 24rpx;"></view>
        </view>
      </scroll-view>

      <!-- 输入区域 -->
      <view class="input-area" :style="{ paddingBottom: safeBottom + 'px' }">
        <!-- 附件预览（不再包进旧的 input-container 里，避免布局被 textarea 影响） -->
        <view v-if="attachments.length > 0" class="attachment-preview-container">
          <view class="attachment-preview-list">
            <!-- 所有附件 - 根据类型显示不同样式 -->
            <view v-for="att in attachments" :key="att.uid">
              <!-- 图片附件 -->
              <view v-if="att.isImage" class="att-item">
                <image :src="att.base64" mode="aspectFill" class="att-thumb" @tap="previewImage(att.base64)" />
                <view class="att-remove" @tap="removeAttachment(att.uid)">
                  <text class="att-remove-text">×</text>
                </view>
              </view>
              <!-- 文档附件 -->
              <view v-else class="att-item-doc">
                <view class="doc-icon-wrap">
                  <text class="doc-icon">{{ getFileIcon(att.type) }}</text>
                </view>
                <view class="doc-info">
                  <text class="doc-name">{{ att.name }}</text>
                  <text class="doc-size">{{ att.size }}</text>
                </view>
                <view class="att-remove" @tap="removeAttachment(att.uid)">
                  <text class="att-remove-text">×</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 底部输入面板（单行输入 + 右侧发送按钮 + 下方快捷标签） -->
        <view class="chat-input-card" :class="{ 'chat-input-card--focused': inputFocused }">
          <view class="chat-input-top-row">
            <!-- 文本输入 -->
            <view v-if="!voiceMode" class="chat-input-textarea-wrap">
              <textarea
                class="chat-input-textarea"
                v-model="inputValue"
                :maxlength="INPUT_MAX_LEN"
                placeholder="请输入消息内容..."
                placeholder-class="chat-input-placeholder"
                :disabled="loading"
                :max-height="72"
                confirm-type="send"
                @confirm="onSend"
                @focus="onInputFocus"
                @blur="onInputBlur"
              />
            </view>

            <!-- 语音长按按钮（UI占位） -->
            <view v-else class="chat-voice-btn" @tap="onVoiceTap" @longpress="onVoiceLongPress">
              <text class="chat-voice-btn-text">长按说话</text>
            </view>
          </view>

          <view class="chat-input-action-row">
            <view class="chat-input-icon-btn" @tap="chooseImage">
              <Paperclip :size="18" :stroke-width="2" color="currentColor" />
              <text class="chat-input-action-label">附件</text>
            </view>

            <!-- 语言按钮：开启后点击输入框切换语音长按按钮 -->
            <view
              class="chat-input-language-btn"
              :class="{ 'chat-input-language-btn--active': voiceUiEnabled }"
              @tap.stop="onLanguageToggle"
            >
              <Mic :size="18" :stroke-width="2" color="currentColor" />
              <text class="chat-input-action-label">语音</text>
            </view>

            <view
              class="chat-input-send-btn"
              :class="{
                'chat-input-send-btn--active': inputValue.trim() || attachments.length > 0,
                'chat-input-send-btn--loading': loading,
              }"
              @tap="onSend"
            >
              <view v-if="loading" class="input-send-spinner"></view>
              <image
                v-else
                src="/static/arrow-top.png"
                mode="aspectFit"
                class="chat-input-send-icon"
              />
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick, onUnmounted  } from 'vue'
import { MessageSquarePlus, Mic, Paperclip } from 'lucide-vue-next'
import {
  TOOL_ICON_COLOR,
  mdHeading,
  mdCode,
  mdCodeBg,
  mdPreBg,
  mdBody,
  mdH3Border,
} from '../../styles/theme-colors.js'
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
let clawStatusInflight = 0
const selectedClawIndex = ref(0)
const deviceSearchQuery = ref('')
const filteredClawList = ref([])
/** 单条消息正文最大字符数（与输入框 maxlength 一致） */
const INPUT_MAX_LEN = 500
const inputValue = ref('')
const attachments = ref([])
const scrollTop = ref(0)
const chatRefresherTriggered = ref(false)
const safeBottom = ref(0)
const inputFocused = ref(false)
const inputRowActive = ref(false);
const sessionOptions = ref([
  { value: 'main', label: '主会话' },
  { value: 'session2', label: '会话 2' },
])
const selectedIndex = ref(0)
const currentSession = ref(sessionOptions.value[selectedIndex.value].value)
const activeDropdown = ref(null)

// 语音输入UI（暂不接入真实录音逻辑）
const voiceUiEnabled = ref(false) // 语言按钮高亮开关
const voiceMode = ref(false) // 当前是否显示语音长按按钮

const onLanguageToggle = () => {
  voiceUiEnabled.value = !voiceUiEnabled.value
  // 语言按钮切换时直接切换到语音/文本，不依赖再点文本框
  voiceMode.value = voiceUiEnabled.value
}

const onVoiceTap = () => {
  voiceMode.value = false
  voiceUiEnabled.value = false
}

const onVoiceLongPress = () => {
  uni.showToast({ title: '语音输入暂未实现', icon: 'none', duration: 1200 })
}

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
  { key: '2', text: '使用指南' },
  { key: '3', text: '技术支持' },
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
const handleClickOutside = (event) => {
  if (typeof document === 'undefined' || !document.querySelectorAll) return
  const pickers = document.querySelectorAll('.tool-picker-wrap')
  let clickedInside = false
  pickers.forEach((picker) => {
    if (picker.contains(event.target)) clickedInside = true
  })
  if (!clickedInside) activeDropdown.value = null
}
/** @param refreshDevicesWhenNoClaw 为 true 时：无选中设备则拉取在线设备列表；为 false 时无设备则静默 return（仅保留给特殊调用） */
const performChatRefresh = async (refreshDevicesWhenNoClaw) => {
  if (!currentClawId.value) {
    if (refreshDevicesWhenNoClaw) {
      await fetchClawStatus()
      uni.showToast({ title: '设备列表已更新', icon: 'none', duration: 1000 })
    }
    return
  }
  if (currentSession.value === 'main') {
    await loadHistory(currentClawId.value)
  } else {
    messages.value = []
  }
  uni.showToast({ title: '已刷新', icon: 'none', duration: 800 })
}

/** 部分端同步设 false 无法结束下拉动画，需在 nextTick / 短延迟后再关 */
const stopChatPullRefresher = () => {
  chatRefresherTriggered.value = false
  nextTick(() => {
    chatRefresherTriggered.value = false
    setTimeout(() => {
      chatRefresherTriggered.value = false
    }, 80)
  })
}

const onChatRefresherRefresh = async () => {
  // 受控 refresher-triggered：必须先置 true 与端上状态对齐，结束后再置 false 才会收起
  chatRefresherTriggered.value = true
  try {
    await performChatRefresh(true)
  } catch (e) {
    console.error('[Chat] 下拉刷新失败', e)
    uni.showToast({ title: '刷新失败', icon: 'none', duration: 1200 })
  } finally {
    stopChatPullRefresher()
  }
}

const refreshChat = async () => {
  uni.showLoading({ title: '刷新中', mask: true })
  try {
    // 与下拉刷新一致：无设备时也会拉在线设备并提示，避免点击无任何反馈
    await performChatRefresh(true)
  } catch (e) {
    console.error('[Chat] 刷新失败', e)
    uni.showToast({ title: '刷新失败', icon: 'none', duration: 1200 })
  } finally {
    uni.hideLoading()
  }
}
onMounted(async () => {
  const sys = uni.getSystemInfoSync()
  if (typeof document !== 'undefined' && document.addEventListener) {
    document.addEventListener('click', handleClickOutside)
  }
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
  if (typeof document !== 'undefined' && document.removeEventListener) {
    document.removeEventListener('click', handleClickOutside)
  }
})
// 设备选择处理函数
const handleClawChange = (e) => {
  const idx = Number(e.detail.value)
  selectedClawIndex.value = idx
  const clawId = clawList.value[idx]?.clawId
  if (clawId) onClawChange(clawId)
}

const handleSessionChange = (e) => {
  selectedIndex.value = Number(e.detail.value)
}

watch(messages, () => {
  nextTick(() => { scrollTop.value = 999999 })
}, { deep: true })

const fetchClawStatus = async () => {
  const userId = currentUser.value?.userId
  if (!userId || !currentToken.value) return
  clawStatusInflight++
  loadingStatus.value = true
  try {
    const data = await getClawStatus(userId, currentToken.value)
    clawList.value = data.clawList || []
    filteredClawList.value = clawList.value
  } catch (e) {
    console.error('[Chat] 获取设备失败：', e)
  } finally {
    clawStatusInflight = Math.max(0, clawStatusInflight - 1)
    if (clawStatusInflight === 0) {
      loadingStatus.value = false
    }
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
  uni.navigateTo({ url: '/pages/login/login', animationType: 'slide-in-right', animationDuration: 200 })
}

const onNewSession = async () => {
  await clearHistory(currentToken.value)
  uni.showToast({ title: '已清空对话', icon: 'none', duration: 1200 })
}

// ============ 文件类型识别和处理函数 ============

// 文件类型配置
const FILE_TYPE_CONFIG = {
  image: {
    exts: ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'],
    icon: '🖼️',
    name: '图片',
    accept: 'image/*'
  },
  word: {
    exts: ['doc', 'docx'],
    icon: '📄',
    name: 'Word',
    accept: '.doc,.docx'
  },
  excel: {
    exts: ['xls', 'xlsx'],
    icon: '📊',
    name: 'Excel',
    accept: '.xls,.xlsx'
  },
  pdf: {
    exts: ['pdf'],
    icon: '📕',
    name: 'PDF',
    accept: '.pdf'
  },
  markdown: {
    exts: ['md', 'markdown'],
    icon: '📝',
    name: 'Markdown',
    accept: '.md,.markdown'
  }
}

// 获取文件类型
const getFileType = (filename) => {
  const ext = filename.split('.').pop()?.toLowerCase()
  for (const [type, config] of Object.entries(FILE_TYPE_CONFIG)) {
    if (config.exts.includes(ext)) return type
  }
  return 'unknown'
}

// 获取文件图标
const getFileIcon = (type) => {
  return FILE_TYPE_CONFIG[type]?.icon || '📎'
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(1) + ' ' + sizes[i]
}

// 验证文件大小（50MB）
const validateFileSize = (bytes) => {
  const MAX_SIZE = 50 * 1024 * 1024 // 50MB
  return bytes <= MAX_SIZE
}

const chooseFile = () => {
  const sysInfo = uni.getSystemInfoSync()
  const platform = sysInfo.platform?.toLowerCase() || ''
  const isH5 = platform === 'web' || platform === 'h5' || typeof document !== 'undefined'
  
  console.log('[Chat] 平台检测:', { platform, isH5 })
  
  // 定义 H5 文件选择函数
  const chooseFileFromH5 = () => {
    const input = document.createElement('input')
    input.type = 'file'
    // 支持所有类型文件
    input.accept = 'image/*,.doc,.docx,.xls,.xlsx,.pdf,.md'
    input.multiple = true
    
    input.onchange = (e) => {
      const files = e.target.files
      if (!files || files.length === 0) return
      
      for (let i = 0; i < files.length; i++) {
        const file = files[i]
        
        // 验证文件大小
        if (!validateFileSize(file.size)) {
          uni.showToast({ title: `文件 ${file.name} 超过 50MB 限制`, icon: 'none', duration: 1000 })
          continue
        }
        
        const type = getFileType(file.name)
        const ext = file.name.split('.').pop()?.toLowerCase()
        const sizeStr = formatFileSize(file.size)
        
        if (type === 'image') {
          // 图片：读取为 base64
          const reader = new FileReader()
          
          reader.onload = (event) => {
            const mime = file.type || 'image/jpeg'
            
            attachments.value.push({
              uid: Date.now() + '-' + i,
              name: file.name,
              size: sizeStr,
              sizeRaw: file.size,
              type: type,
              ext: ext,
              base64: event.target.result,
              isImage: true
            })
            console.log('[Chat] H5 图片上传成功:', file.name, sizeStr)
          }
          
          reader.onerror = () => {
            console.error('[Chat] 文件读取失败:', file.name)
            uni.showToast({ title: `文件 ${file.name} 读取失败`, icon: 'none', duration: 1000 })
          }
          
          reader.readAsDataURL(file)
        } else {
          // 文档：仅记录元数据，不读取内容
          attachments.value.push({
            uid: Date.now() + '-' + i,
            name: file.name,
            size: sizeStr,
            sizeRaw: file.size,
            type: type,
            ext: ext,
            base64: null,
            isImage: false
          })
          console.log('[Chat] H5 文件上传成功:', file.name, sizeStr)
        }
      }
    }
    
    input.click()
  }
  
  // 定义原生平台文件选择函数
  const chooseFileFromNative = () => {
    // 原生平台：使用 uni.chooseFile 支持所有文件类型
    uni.chooseFile({
      count: 9999,  // 无限制
      type: 'all',
      success: (res) => {
        console.log('[Chat] uni.chooseFile 成功，返回:', res)
        res.tempFilePaths?.forEach((path, i) => {
          // 验证文件大小
          if (res.tempFiles && res.tempFiles[i]) {
            const fileSize = res.tempFiles[i].size || 0
            if (!validateFileSize(fileSize)) {
              uni.showToast({ title: `文件超过 50MB 限制`, icon: 'none', duration: 1000 })
              return
            }
          }
          
          const filename = path.split('/').pop() || 'unknown'
          const type = getFileType(filename)
          const ext = filename.split('.').pop()?.toLowerCase()
          
          // 尝试使用 FileSystemManager
          if (uni.getFileSystemManager && typeof uni.getFileSystemManager === 'function') {
            try {
              uni.getFileSystemManager().readFile({
                filePath: path,
                encoding: 'base64',
                success: (r) => {
                  const sizeStr = formatFileSize(r.data.length)
                  
                  if (type === 'image') {
                    const mime = getImageMimeType(ext)
                    attachments.value.push({
                      uid: Date.now() + '-' + i,
                      name: filename,
                      size: sizeStr,
                      sizeRaw: r.data.length,
                      type: type,
                      ext: ext,
                      base64: `data:${mime};base64,${r.data}`,
                      isImage: true
                    })
                  } else {
                    // 文档：不存储 base64，因为太大
                    attachments.value.push({
                      uid: Date.now() + '-' + i,
                      name: filename,
                      size: sizeStr,
                      sizeRaw: r.data.length,
                      type: type,
                      ext: ext,
                      base64: null,
                      isImage: false
                    })
                  }
                  console.log('[Chat] 原生平台文件读取成功:', filename, sizeStr)
                },
                fail: (err) => {
                  console.error('[Chat] FileSystemManager 读取失败:', err)
                  uni.showToast({ title: `文件 ${filename} 读取失败`, icon: 'none', duration: 1000 })
                }
              })
            } catch (e) {
              console.error('[Chat] FileSystemManager 异常:', e)
              uni.showToast({ title: '文件读取异常', icon: 'none', duration: 1000 })
            }
          } else {
            uni.showToast({ title: '该平台不支持文件读取', icon: 'none', duration: 1000 })
          }
        })
      },
      fail: (err) => {
        console.error('[Chat] uni.chooseFile 失败:', err)
      }
    })
  }
  
  // 根据平台选择对应的处理方式
  if (isH5) {
    console.log('[Chat] 使用 H5 文件选择')
    chooseFileFromH5()
  } else {
    console.log('[Chat] 使用原生平台文件选择')
    chooseFileFromNative()
  }
}

// 获取图片 MIME 类型
const getImageMimeType = (ext) => {
  const mimeMap = {
    'jpg': 'image/jpeg',
    'jpeg': 'image/jpeg',
    'png': 'image/png',
    'gif': 'image/gif',
    'bmp': 'image/bmp',
    'webp': 'image/webp'
  }
  return mimeMap[ext?.toLowerCase()] || 'image/jpeg'
}

// 保留 chooseImage 作为向后兼容的别名
const chooseImage = chooseFile

const removeAttachment = (uid) => {
  attachments.value = attachments.value.filter(a => a.uid !== uid)
}

const previewImage = (src) => {
  uni.previewImage({ urls: [src], current: src })
}

const onSend = async () => {
  let text = inputValue.value.trim()
  if (text.length > INPUT_MAX_LEN) text = text.slice(0, INPUT_MAX_LEN)
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
  inputValue.value = String(text).slice(0, INPUT_MAX_LEN)
  onSend()
}

const onQuickItemTap = (key) => {
  const map = { '1': '项目介绍', '2': '使用指南', '3': '技术支持' }
  inputValue.value = String(map[key] || '').slice(0, INPUT_MAX_LEN)
  onSend()
}

const onInputFocus = () => {
  inputFocused.value = true
  inputRowActive.value = true
}

const onInputBlur = () => {
  inputFocused.value = false
  inputRowActive.value = false
}

const renderMarkdown = (content) => {
  if (!content) return ''
  return content
    .replace(/```[\w]*\n?([\s\S]*?)```/g, (_, code) => `<pre style="background:${mdPreBg};padding:16rpx;border-radius:8rpx;margin:8rpx 0;overflow:auto"><code style="font-size:22rpx;font-family:monospace;color:${mdBody}">${code}</code></pre>`)
    .replace(/`([^`]+)`/g, (_, t) => `<text style="background:${mdCodeBg};padding:2rpx 6rpx;border-radius:4rpx;font-size:0.9em;color:${mdCode}">${t}</text>`)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\*([^*]+)\*/g, '<em>$1</em>')
    .replace(/^### (.+)$/gm, (_, t) => `<text style="font-weight:600;color:${mdBody};padding-left:8rpx;border-left:3px solid ${mdH3Border};display:block;margin:8rpx 0">${t}</text>`)
    .replace(/^## (.+)$/gm, (_, t) => `<text style="font-weight:600;font-size:1.1em;color:${mdHeading};display:block;margin:10rpx 0">${t}</text>`)
    .replace(/^# (.+)$/gm, (_, t) => `<text style="font-weight:700;font-size:1.2em;color:${mdHeading};display:block;margin:12rpx 0">${t}</text>`)
    .replace(/^[*\-] (.+)$/gm, '<text style="padding-left:20rpx;display:block;margin:4rpx 0">• $1</text>')
    .replace(/\n/g, '<br/>')
}
</script>

<style lang="less" scoped>
@import '../../styles/theme.less';

.chat-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  background: @ohsd-app-page-bg;
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

/* 顶栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12rpx 28rpx 16rpx;
  padding-top: calc(12rpx + var(--status-bar-height, 0px));
  background: @ohsd-bg-surface;
  flex-shrink: 0;
  border-bottom: 1rpx solid @ohsd-border-hairline;
}

.top-bar-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
}

.top-logo {
  width: 52rpx;
  height: 52rpx;
  border-radius: 14rpx;
  background: @ohsd-bg-ios;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.top-logo-img {
  width: 30rpx;
  height: 30rpx;
}

.top-brand {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  min-width: 0;
}

.top-brand-name {
  font-size: 28rpx;
  font-weight: 700;
  color: @ohsd-text-primary;
  line-height: 1.2;
}

.top-brand-sub {
  font-size: 22rpx;
  color: @ohsd-text-subtle;
  line-height: 1.2;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
  flex-shrink: 0;
}

/* 工具行（横向滚动） */
.tool-scroll {
  width: 100%;
  flex-shrink: 0;
  white-space: nowrap;
  border-bottom: 1rpx solid @ohsd-border-hairline;
  background: @ohsd-bg-page;
}

.tool-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 24rpx 14rpx;
  box-sizing: border-box;
  min-width: 100%;
}

/* 会话 + 刷新/新对话图标靠右，左侧保留设备选择 */
.tool-row-trail {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-left: auto;
  flex-shrink: 0;
}

.tool-picker-wrap {
  display: inline-flex;
  flex-shrink: 0;
}

.tool-picker-wrap.active .tool-chip {
  border-color: @ohsd-primary;
  box-shadow: 0 0 0 2rpx @ohsd-primary-muted;
}

.tool-chip {
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 64rpx;
  padding: 0 20rpx;
  border-radius: 12rpx;
  border: 1rpx solid @ohsd-border-light;
  background: @ohsd-white;
  box-sizing: border-box;
}

.tool-chip--device {
  min-width: 200rpx;
  max-width: 320rpx;
}

.tool-chip--session {
  min-width: 160rpx;
}

.tool-chip--disabled {
  background: @ohsd-fill-03;
  border-style: dashed;
}

.tool-chip-label {
  font-size: 24rpx;
  color: @ohsd-text-75;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tool-caret {
  width: 26rpx;
  height: 26rpx;
  flex-shrink: 0;
  opacity: 0.55;
}

.tool-icon-btn {
  width: 64rpx;
  height: 64rpx;
  border-radius: 12rpx;
  background: @ohsd-white;
  border: 1rpx solid @ohsd-border-light;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tool-icon-img {
  width: 36rpx;
  height: 36rpx;
  opacity: 0.65;
}

.ws-badge {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 12rpx;
  border-radius: 20rpx;
  background: @ohsd-fill-04;
  white-space: nowrap;
  flex-shrink: 0;
}

.ws-badge.connected {
  background: @ohsd-success-muted;
}

.ws-badge.disconnected {
  background: @ohsd-danger-muted;
}

.ws-badge.connected .ws-dot {
  background: @ohsd-success;
  box-shadow: 0 0 0 3rpx @ohsd-success-ring;
}

.ws-badge.disconnected .ws-dot {
  background: @ohsd-color-error;
}

.ws-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.ws-text {
  font-size: 20rpx;
  color: @ohsd-text-60;
  white-space: nowrap;
}

.user-btn {
  padding: 6rpx;
}

.user-avatar {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, @ohsd-color-error, @ohsd-danger-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-avatar-text {
  font-size: 20rpx;
  font-weight: 700;
  color: @ohsd-white;
}

/* 用户面板 */
.panel-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: @ohsd-overlay-light;
}

.user-panel {
  position: fixed;
  top: calc(152rpx + var(--status-bar-height, 0px));
  left: 24rpx;
  right: 24rpx;
  width: auto;
  max-height: 72vh;
  background: @ohsd-white;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 32rpx @ohsd-border-default;
  z-index: 101;
  animation: none;
}

@keyframes panelPopIn {
  from {
    opacity: 0;
    transform: translateY(14rpx) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes panelPopOut {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  to {
    opacity: 0;
    transform: translateY(10rpx) scale(0.98);
  }
}

.userPanelPop-enter-active {
  animation: panelPopIn 0.18s ease-out both;
}

.userPanelPop-leave-active {
  animation: panelPopOut 0.14s ease-in both;
}

@keyframes maskFadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes maskFadeOut {
  from {
    opacity: 1;
  }
  to {
    opacity: 0;
  }
}

.userPanelMask-enter-active {
  animation: maskFadeIn 0.14s ease-out both;
}

.userPanelMask-leave-active {
  animation: maskFadeOut 0.12s ease-in both;
}

.panel-user-head {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 28rpx 24rpx 24rpx;
  border-bottom: 1rpx solid @ohsd-chat-divider;
}

.panel-avatar {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, @ohsd-color-error, @ohsd-danger-deep);
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel-avatar-text {
  font-size: 24rpx;
  font-weight: 700;
  color: @ohsd-white;
}

.panel-user-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.panel-username {
  font-size: 26rpx;
  font-weight: 600;
  color: @ohsd-markdown-heading;
}

.panel-userid {
  font-size: 20rpx;
  color: @ohsd-text-caption-hex;
}

.panel-section {
  padding: 26rpx 24rpx 0;
}

.panel-section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.panel-section-title {
  font-size: 18rpx;
  font-weight: 600;
  color: @ohsd-text-caption-hex;
  letter-spacing: 1rpx;
  display: block;
  margin-bottom: 16rpx;
}

.panel-refresh {
  font-size: 20rpx;
  color: @ohsd-primary;
  margin-bottom: 16rpx;
}

.input-send-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  background-color: @ohsd-primary-soft;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: 8rpx;
  flex-shrink: 0;
}

.input-send-btn--active {
  background-color: @ohsd-primary;
}

.input-send-btn--loading {
  background-color: @ohsd-primary;
  opacity: 0.85;
}

.input-send-spinner {
  width: 32rpx;
  height: 32rpx;
  border: 3rpx solid @ohsd-on-primary-ring;
  border-top-color: @ohsd-white;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.input-send-icon {
  width: 34rpx;
  height: 34rpx;
}

.token-box {
  background: @ohsd-bg-muted;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  margin-bottom: 16rpx;
  max-height: 100rpx;
  overflow: hidden;
}

.token-text {
  font-size: 18rpx;
  font-family: monospace;
  color: @ohsd-text-body-hex;
  word-break: break-all;
  line-height: 1.5;
}

.token-tip {
  background: @ohsd-surface-tint-blue;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  margin-bottom: 16rpx;
}

.token-tip-text {
  font-size: 18rpx;
  color: @ohsd-primary;
  line-height: 1.5;
}

.copy-btn {
  background: @ohsd-fill-05;
  border-radius: 10rpx;
  padding: 12rpx;
  text-align: center;
  margin-bottom: 24rpx;
}

.copy-btn-text {
  font-size: 22rpx;
  color: @ohsd-text-60;
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
  background: @ohsd-bg-page;
  border-radius: 12rpx;
  font-size: 22rpx;
  color: @ohsd-text-near-primary;
  margin-bottom: 16rpx;
}

.device-search-placeholder {
  color: @ohsd-text-ghost;
  font-size: 22rpx;
}

.device-select {
  padding: 16rpx;
  background: @ohsd-bg-page;
  border-radius: 12rpx;
  text-align: center;
}


.device-select-placeholder {
  font-size: 22rpx;
  line-height: 1.4;
  color: @ohsd-text-60;
}

.claw-empty {
  padding: 16rpx;
  background: @ohsd-bg-page;
  border-radius: 12rpx;
  text-align: center;
  margin-bottom: 16rpx;
}

.claw-empty-text {
  font-size: 22rpx;
  color: @ohsd-text-dim-hex;
}

.claw-item {
  padding: 14rpx 18rpx;
  background: @ohsd-claw-card-bg;
  border: 1rpx solid @ohsd-claw-card-border;
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
  background: @ohsd-success;
}

.claw-id {
  font-size: 22rpx;
  font-weight: 600;
  color: @ohsd-markdown-heading;
}

.claw-heartbeat {
  font-size: 18rpx;
  color: @ohsd-text-hint-hex;
}

.logout-btn {
  margin: 20rpx 24rpx 24rpx;
  padding: 18rpx;
  background: @ohsd-danger-faint;
  border-radius: 12rpx;
  text-align: center;
}

.logout-btn-text {
  font-size: 24rpx;
  color: @ohsd-color-error;
  font-weight: 500;
}

/* 设备选择栏 */
.toolbar {
  display: flex;
  align-items: center;
  padding: 10rpx 20rpx;
  background: @ohsd-bg-page;
  border-bottom: 1rpx solid @ohsd-border-subtle-04;
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
  background: @ohsd-fill-04;
  border-radius: 100rpx;
  font-size: 22rpx;
  color: @ohsd-text-near-primary;
  flex: 1;
}

.device-search-placeholder {
  color: @ohsd-text-ghost;
  font-size: 22rpx;
}

.device-select-bar {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  border-radius: 10rpx;
}

.device-select-bar.active {
  border: 2rpx solid @ohsd-primary;
  box-shadow: 0 0 0 2rpx @ohsd-primary-ring;
}

.device-select-input {
  display: flex;
  gap: 6rpx;
  align-items: center;
  padding: 6rpx 12rpx;
  border-radius: 10rpx;
  border: 1rpx solid @ohsd-border-light;
  max-width: 220rpx;
}

.device-select-placeholder {
  font-size: 22rpx;
  color: @ohsd-text-65;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 160rpx;
}

.device-select-placeholder {
  font-size: 22rpx;
  color: @ohsd-text-60;
}

/* 设备选择框空状态 */
.device-select-empty {
  display: flex;
  align-items: center;
  padding: 8rpx 16rpx;
  border-radius: 10rpx;
  border: 1rpx solid @ohsd-border-light;
  background: @ohsd-fill-02;
}

.device-empty-text {
  font-size: 22rpx;
  color: @ohsd-text-tertiary;
}

/* 快捷功能栏 */
.quick-bar {
  display: flex;
  align-items: center;
  padding-bottom: 6rpx;
  flex-shrink: 0;
  gap: 12rpx;
  overflow-x: auto;
}

.quick-item {
  padding: 10rpx 22rpx;
  background-color: @ohsd-bg-page;
  border: 1rpx solid @ohsd-border-subtle-08;
  border-radius: 999rpx;
  flex-shrink: 0;
  transition: background-color 0.15s ease;
}

.quick-item-active {
  background-color: @ohsd-chat-thumb;
  /* 深灰（比原始浅灰深一级） */
  box-shadow: inset 0 2rpx 4rpx @ohsd-border-light;
  /* 内阴影反馈 */
  transition: background-color 0.2s ease;
  /* 平滑过渡 */
}

.quick-item-text {
  font-size: 24rpx;
  color: @ohsd-text-secondary;
  line-height: 1.4;
  white-space: nowrap;
}

/* 聊天区域 */
.chat-scroll {
  flex: 1;
  min-height: 0;
}

.welcome-wrap {
  padding: 32rpx 28rpx 24rpx;
}

.welcome-header {
  display: flex;
  align-items: flex-start;
  margin-bottom: 28rpx;
  max-width: 100%;
}

.welcome-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 20rpx;
  flex-shrink: 0;
}

.welcome-icon {
  width: 80rpx;
  height: 80rpx;
}

.welcome-title {
  font-size: 32rpx;
  font-weight: 600;
  color: @ohsd-text-primary;
  margin-bottom: 8rpx;
}

.welcome-text-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  flex: 1;
  min-width: 0;
}

.welcome-desc {
  font-size: 24rpx;
  color: @ohsd-text-tertiary;
  line-height: 1.5;
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
  background: linear-gradient(123deg, @ohsd-welcome-grad-start 0%, @ohsd-welcome-grad-end 100%);
  border-radius: 16rpx;
  padding: 24rpx 26rpx;
  width: 100%;
}

/* 分组容器 */
.prompt-group {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

/* 分组标题 */
.prompt-group-title {
  font-size: 26rpx;
  font-weight: 600;
  color: @ohsd-text-65;
  margin-bottom: 8rpx;
}

/* 热门话题问题（透明背景） */
.prompt-question {
  font-size: 26rpx;
  color: @ohsd-text-secondary;
  line-height: 1.55;
  background-color: transparent;
  padding: 8rpx 0;
  border-bottom: 1rpx solid @ohsd-border-hairline;
}

.prompt-question:last-child {
  border-bottom-width: 0;
}

/* 设计指南卡片（半透明白色背景） */
.prompt-card {
  display: flex;
  /* 启用Flex布局 */
  flex-direction: column;
  /* 垂直排列子元素 */
  background-color: @ohsd-glass-surface;
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
  color: @ohsd-text-body-hex;
  margin-bottom: 8rpx;
  /* 标题与描述间距 */
}

/* 设计指南卡片描述 */
.prompt-card-desc {
  font-size: 22rpx;
  color: @ohsd-markdown-blockquote-text;
  line-height: 1.5;
}

/* 设计指南卡片 hover 效果 */
.prompt-card:hover {
  background-color: @ohsd-glass-surface-hover;
  /*  hover时更不透明 */
}

/* 设计指南卡片（白色背景圆角） */
.prompt-card {
  background-color: @ohsd-white;
  /* 白色背景 */
  border-radius: 12rpx;
  /* 圆角 */
  border: 1rpx solid @ohsd-chat-border-soft;
  /* 浅边框 */
  padding: 16rpx;
  cursor: pointer;
  transition: border-color 0.2s;
}

/* 设计指南卡片标题 */
.prompt-card-title {
  font-size: 24rpx;
  font-weight: 500;
  color: @ohsd-text-body-hex;
  margin-bottom: 8rpx;
}

/* 设计指南卡片描述 */
.prompt-card-desc {
  font-size: 22rpx;
  color: @ohsd-markdown-blockquote-text;
  line-height: 1.5;
}

/* 设计指南卡片 hover 效果 */
.prompt-card:hover {
  border-color: @ohsd-primary;
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
  background: @ohsd-chat-input-well;
  border: 1rpx solid @ohsd-border-hairline;
}

.user-avatar-msg {
  background: linear-gradient(135deg, @ohsd-color-error, @ohsd-danger-deep);
  box-shadow: 0 2rpx 8rpx @ohsd-danger-shadow;
}

.user-avatar-msg-text {
  font-size: 22rpx;
  font-weight: 700;
  color: @ohsd-white;
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
  max-width: 75vw;
  margin-left: 20rpx;
  word-wrap: break-word;
  word-break: break-word;
}

.bubble-wrap-assistant {
  align-items: flex-start;
  max-width: 75vw;
  margin-right: 20rpx;
  min-width: 0;
  flex: 1;
  word-wrap: break-word;
  word-break: break-word;
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
  background: @ohsd-bg-ios;
  border-bottom-right-radius: 6rpx;
  box-shadow: 0 2rpx 8rpx @ohsd-shadow-message;
}

/* AI 气泡：灰色背景 */
.bubble-assistant {
  background: @ohsd-bg-ios;
  border-bottom-left-radius: 6rpx;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.bubble-text-user {
  font-size: 27rpx;
  color: @ohsd-text-strong-hex;
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
  background: @ohsd-fill-25;
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
  color: @ohsd-text-82;
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
  background: @ohsd-white;
  padding: 12rpx 20rpx 20rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  width: 100%;
  transition: all 0.3s ease;
}

/* 底部单行输入面板（替代旧的 input-container / textarea 布局） */
.chat-input-card {
  width: 100%;
  background: @ohsd-white;
  border-radius: 28rpx;
  padding: 16rpx 18rpx 14rpx;
  box-sizing: border-box;
  border: 1rpx solid @ohsd-border-hairline;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  transition: all 0.2s ease;
}

.chat-input-card--focused {
  border-color: @ohsd-primary;
}

.chat-input-top-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.chat-input-icon-btn {
  height: 52rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-shrink: 0;
  background: @ohsd-bg-page;
  border: 1rpx solid @ohsd-border-light;
  padding: 0 16rpx;
  gap: 10rpx;
  white-space: nowrap;
  color: @ohsd-text-secondary;
}

.chat-input-input {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  box-sizing: border-box;
  font-size: 24rpx;
  color: @ohsd-text-near-primary;
  padding: 0 12rpx 0 0;
  line-height: 72rpx;
  background: transparent;
}

.chat-input-textarea {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  min-height: 72rpx;
  max-height: 72rpx !important;
  font-size: 24rpx;
  color: @ohsd-text-near-primary;
  line-height: 1.4;
  background: transparent;
  padding: 18rpx 0;
  border: none;
  outline: none;
  overflow-y: auto;
  overflow-x: hidden;
  resize: none;
}

.chat-input-textarea-wrap {
  flex: 1;
  min-width: 0;
}

.chat-input-placeholder {
  color: @ohsd-text-25;
  font-size: 24rpx;
  line-height: 1.4;
}

.chat-input-send-btn {
  width: 52rpx;
  height: 52rpx;
  border-radius: 18rpx;
  background-color: @ohsd-primary-soft;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.chat-input-send-btn--active {
  background-color: @ohsd-primary;
}

.chat-input-send-btn--loading {
  background-color: @ohsd-primary;
  opacity: 0.85;
}

.chat-input-send-icon {
  width: 34rpx;
  height: 34rpx;
}

.chat-voice-btn {
  flex: 1;
  min-width: 0;
  height: 72rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-left: 0;
  background: @ohsd-bg-page;
  border: 1rpx solid @ohsd-border-light;
  color: @ohsd-text-secondary;
}

.chat-voice-btn-text {
  font-size: 24rpx;
  color: inherit;
  line-height: 1;
}

.chat-input-action-label {
  font-size: 22rpx;
  color: inherit;
  line-height: 1;
  flex-shrink: 0;
}

.chat-input-language-btn {
  height: 52rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-shrink: 0;
  background: @ohsd-bg-page;
  border: 1rpx solid @ohsd-border-light;
  padding: 0 16rpx;
  gap: 10rpx;
  white-space: nowrap;
  color: @ohsd-text-secondary;
}

.chat-input-language-btn--active {
  background-color: @ohsd-primary;
  border-color: @ohsd-primary;
  color: #ffffff;
}

.chat-quick-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  flex-wrap: wrap;
}

.chat-input-action-row {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 12rpx;
}

.chat-input-send-btn {
  margin-left: auto;
}

/* 输入框容器 - 包含预览和输入框 */
.input-container {
  display: flex;
  flex-direction: column;
  gap: 0;
  border: 2rpx solid @ohsd-border-strong;
  border-radius: 20rpx;
  transition: all 0.3s ease;
  overflow: hidden;
}

.input-container.has-attachments {
  border-color: @ohsd-primary;
}

.input-container.focused {
  border-color: @ohsd-primary;
}

/* 附件预览容器 */
.attachment-preview-container {
  width: 100%;
  background: @ohsd-fill-02;
  padding: 12rpx 12rpx 12rpx 12rpx;
  border-bottom: 1rpx solid @ohsd-border-hairline;
}

.attachment-preview-list {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
  width: 100%;
  justify-content: center;
}

/* 附件项 - 图片 */
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
  border: 1rpx solid @ohsd-border-subtle-08;
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

/* 附件项 - 文档 */
.att-item-doc {
  width: 120rpx;
  background: @ohsd-fill-04;
  border-radius: 8rpx;
  border: 1rpx solid @ohsd-border-subtle-08;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  padding: 8rpx 6rpx;
  position: relative;
  flex-shrink: 0;
  animation: attachmentSlideIn 0.3s ease-out;
  overflow: hidden;
}

.doc-icon-wrap {
  font-size: 36rpx;
  line-height: 1;
  margin-top: 2rpx;
}

.doc-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.doc-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  min-height: 0;
  width: 100%;
}

.doc-name {
  font-size: 18rpx;
  color: @ohsd-text-near-primary;
  line-height: 1.3;
  word-break: break-all;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  max-width: 100%;
  text-align: center;
  padding: 0 2rpx;
}

.doc-size {
  font-size: 16rpx;
  color: @ohsd-text-tertiary;
  line-height: 1.2;
}

/* 删除按钮 */
.att-remove {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  background: @ohsd-fill-55;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
  cursor: pointer;
  z-index: 10;
}

.att-remove:active {
  background: @ohsd-fill-75;
  transform: scale(1.1);
}

.att-remove-text {
  font-size: 20rpx;
  color: @ohsd-white;
  line-height: 1;
  font-weight: bold;
}

.input-field-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: stretch;
  overflow: hidden;
}

.input-text-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
  justify-content: stretch;
  min-height: 72rpx;
  max-height: 120rpx;
  overflow: hidden;
}

.input-char-count {
  flex-shrink: 0;
  align-self: center;
  font-size: 20rpx;
  line-height: 1.2;
  color: @ohsd-text-muted;
  padding-right: 6rpx;
  margin: 0;
}

.input-char-count--max {
  color: @ohsd-color-error;
  font-weight: 500;
}

.input-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0 4rpx 0 18rpx;
  min-height: 80rpx;
  transition: all 0.2s ease;
}

.input-row.active {
  background: transparent;
}

.input-container.has-attachments .input-row {
  border-top: 1rpx solid @ohsd-border-hairline;
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
  color: @ohsd-text-50;
  line-height: 1;
}

.input-box {
  flex: 1;
  width: 100%;
  display: block;
  font-size: 24rpx;
  color: @ohsd-text-near-primary;
  background: transparent;
  min-height: 72rpx;
  max-height: 120rpx !important;
  box-sizing: border-box;
  line-height: 1.4;
  padding: 8rpx 0;
  border: none;
  outline: none;
  overflow-y: auto;
  overflow-x: hidden;
  resize: none;
}

.input-box--empty {
  /* 空态 placeholder 需要单独校准垂直居中 */
  padding: 19rpx 0;
  height: 72rpx !important;
  min-height: 72rpx !important;
  max-height: 72rpx !important;
  overflow-y: hidden;
}

.input-box:focus {
  border-color: @ohsd-primary;
  box-shadow: 0 0 0 2px @ohsd-primary-focus-glow;
}

.input-placeholder {
  color: @ohsd-text-25;
  line-height: 1.4;
  font-size: 24rpx;
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
