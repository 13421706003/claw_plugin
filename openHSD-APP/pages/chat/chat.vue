<template>
  <view class="chat-page">

    <!-- ===== 顶部导航栏 ===== -->
    <view class="header">
      <view class="header-left">
        <view class="header-logo">
          <image
            src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
            mode="aspectFit"
            style="width: 32rpx; height: 32rpx;"
          />
        </view>
        <text class="header-title">openHSD</text>
      </view>
      <view class="header-right">
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

    <!-- ===== 用户面板 ===== -->
    <view v-if="showUserPanel" class="panel-mask" @tap="showUserPanel = false"></view>
    <scroll-view v-if="showUserPanel" class="user-panel" scroll-y>
      <view class="panel-user-head">
        <view class="panel-avatar">
          <text class="panel-avatar-text">{{ userInitial }}</text>
        </view>
        <view class="panel-user-info">
          <text class="panel-username">{{ userStore.user?.username }}</text>
          <text class="panel-userid">ID: {{ userStore.user?.userId }}</text>
        </view>
      </view>
      <view class="panel-section">
        <text class="panel-section-title">TOKEN</text>
        <view class="token-box">
          <text class="token-text" selectable>{{ userStore.token }}</text>
        </view>
        <view class="copy-btn" @tap="copyToken">
          <text class="copy-btn-text">{{ tokenCopied ? '已复制!' : '复制' }}</text>
        </view>
      </view>
      <view class="panel-section">
        <view class="panel-section-header">
          <text class="panel-section-title">在线设备</text>
          <text class="panel-refresh" @tap="fetchClawStatus">{{ loadingStatus ? '刷新中' : '刷新' }}</text>
        </view>
        <view v-if="clawList.length === 0" class="claw-empty">
          <text class="claw-empty-text">暂无在线设备</text>
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

    <!-- ===== 设备选择栏 ===== -->
    <view class="toolbar">
      <scroll-view scroll-x class="device-scroll" show-scrollbar="false">
        <view class="device-list">
          <view v-if="clawList.length === 0" class="device-item device-empty">
            <text class="device-item-text">无设备</text>
          </view>
          <view
            v-for="claw in clawList"
            :key="claw.clawId"
            class="device-item"
            :class="{ active: selectedClawId === claw.clawId }"
            @tap="onClawChange(claw.clawId)"
          >
            <view class="device-dot"></view>
            <text class="device-item-text">{{ claw.clawId }}</text>
          </view>
        </view>
      </scroll-view>
      <view class="new-session-btn" @tap="onNewSession">
        <text class="new-session-text">新建</text>
      </view>
    </view>

    <!-- ===== 聊天区域 ===== -->
    <scroll-view
      class="chat-scroll"
      scroll-y
      :scroll-top="scrollTop"
      :scroll-with-animation="true"
      show-scrollbar="false"
    >
      <!-- 欢迎界面 -->
      <view v-if="messages.length === 0" class="welcome-wrap">
        <view class="welcome-icon-wrap">
          <image
            src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*s5sNRo5LjfQAAAAAAAAAAAAADgCCAQ/fmt.webp"
            mode="aspectFit"
            class="welcome-icon"
          />
        </view>
        <text class="welcome-title">你好，我是 AI 助手</text>
        <text class="welcome-desc">通过 openHSD 网关与 AI 对话</text>
        <view class="prompt-cards">
          <view
            v-for="prompt in quickPrompts"
            :key="prompt.key"
            class="prompt-card"
            @tap="onPromptTap(prompt.text)"
          >
            <text class="prompt-card-text">{{ prompt.text }}</text>
          </view>
        </view>
      </view>

      <!-- 消息列表 -->
      <view v-else class="msg-list">
        <view
          v-for="(msg, idx) in messages"
          :key="msg.messageId || idx"
          class="msg-row"
          :class="msg.role === 'user' ? 'msg-row-user' : 'msg-row-assistant'"
        >
          <view v-if="msg.role === 'assistant'" class="msg-avatar assistant-avatar">
            <image
              src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
              mode="aspectFit"
              style="width: 28rpx; height: 28rpx;"
            />
          </view>
          <view class="msg-bubble-wrap" :class="msg.role === 'user' ? 'bubble-wrap-user' : 'bubble-wrap-assistant'">
            <view v-if="msg.role === 'user' && msg.attachments && msg.attachments.length > 0" class="attachment-row">
              <image
                v-for="(att, ai) in msg.attachments"
                :key="ai"
                :src="att.base64"
                mode="aspectFill"
                class="attachment-img"
                @tap="previewImage(att.base64)"
              />
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

    <!-- ===== 输入区域 ===== -->
    <view class="input-area" :style="{ paddingBottom: safeBottom + 'px' }">
      <scroll-view v-if="attachments.length > 0" scroll-x class="attachment-preview" show-scrollbar="false">
        <view class="attachment-preview-list">
          <view v-for="att in attachments" :key="att.uid" class="att-thumb-wrap">
            <image :src="att.base64" mode="aspectFill" class="att-thumb" />
            <view class="att-remove" @tap="removeAttachment(att.uid)">
              <text class="att-remove-text">×</text>
            </view>
          </view>
        </view>
      </scroll-view>
      <scroll-view scroll-x class="chip-scroll" show-scrollbar="false">
        <view class="chip-list">
          <view v-for="chip in senderChips" :key="chip.key" class="chip" @tap="onPromptTap(chip.text)">
            <text class="chip-text">{{ chip.text }}</text>
          </view>
        </view>
      </scroll-view>
      <view class="input-row">
        <view class="input-action-btn" @tap="chooseImage">
          <text class="input-action-icon">+</text>
        </view>
        <textarea
          class="input-box"
          v-model="inputValue"
          placeholder="发送消息..."
          placeholder-class="input-placeholder"
          :disabled="loading"
          auto-height
          :max-height="120"
          confirm-type="send"
          @confirm="onSend"
        />
        <view
          class="send-btn"
          :class="{ 'send-btn-active': inputValue.trim() || attachments.length > 0 }"
          @tap="onSend"
        >
          <view v-if="loading" class="send-spinner"></view>
          <text v-else class="send-icon">↑</text>
        </view>
      </view>
    </view>

  </view>
</template>

<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useUserStore } from '../../store/user.js'
import { getClawStatus } from '../../api/claw.js'
import {
  loading, messages, currentClawId,
  isConnected, connect, disconnect,
  send, selectClaw, clearHistory
} from '../../api/aiService.js'

const userStore = useUserStore()

const showUserPanel  = ref(false)
const tokenCopied    = ref(false)
const clawList       = ref([])
const loadingStatus  = ref(false)
const selectedClawId = ref(null)
const inputValue     = ref('')
const attachments    = ref([])
const scrollTop      = ref(0)
const safeBottom     = ref(0)

const userInitial = computed(() => (userStore.user?.username || 'U').charAt(0).toUpperCase())

const quickPrompts = [
  { key: '1', text: '这个项目是什么？' },
  { key: '2', text: '如何使用？' },
  { key: '3', text: '有哪些功能？' },
  { key: '4', text: '技术特点？' },
]
const senderChips = [
  { key: '1', text: '介绍' },
  { key: '2', text: '演示' },
  { key: '3', text: '指南' },
]

onMounted(async () => {
  const sys = uni.getSystemInfoSync()
  safeBottom.value = sys.safeAreaInsets?.bottom || 0
  connect()
  await fetchClawStatus()
  if (clawList.value.length > 0 && !selectedClawId.value) {
    onClawChange(clawList.value[0].clawId)
  }
})

watch(messages, () => {
  nextTick(() => { scrollTop.value = 999999 })
}, { deep: true })

const fetchClawStatus = async () => {
  const userId = userStore.user?.userId
  if (!userId) return
  loadingStatus.value = true
  try {
    const data = await getClawStatus(userId)
    clawList.value = data.clawList || []
  } catch (e) {
    console.error('[Chat] 获取设备失败：', e)
  } finally {
    loadingStatus.value = false
  }
}

const onClawChange = async (clawId) => {
  selectedClawId.value = clawId
  await selectClaw(clawId)
}

const formatHeartbeat = (ts) => {
  if (!ts) return '未知'
  const diff = Date.now() - ts
  if (diff < 60000) return `${Math.floor(diff / 1000)}s前`
  return `${Math.floor(diff / 60000)}min前`
}

const copyToken = () => {
  uni.setClipboardData({
    data: userStore.token,
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
  await clearHistory()
  uni.showToast({ title: '已清空', icon: 'none', duration: 1200 })
}

const chooseImage = () => {
  uni.chooseImage({
    count: 3,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: (res) => {
      res.tempFilePaths.forEach((path, i) => {
        uni.getFileSystemManager().readFile({
          filePath: path,
          encoding: 'base64',
          success: (r) => {
            const ext = path.split('.').pop()?.toLowerCase() || 'jpg'
            const mime = ext === 'png' ? 'image/png' : 'image/jpeg'
            attachments.value.push({
              uid: Date.now() + '-' + i,
              name: `image_${Date.now()}.${ext}`,
              base64: `data:${mime};base64,${r.data}`
            })
          }
        })
      })
    }
  })
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
  await send(text, atts)
}

const onPromptTap = (text) => {
  inputValue.value = text
  onSend()
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

/* ===== Header ===== */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24rpx;
  height: 88rpx;
  background: #ffffff;
  border-bottom: 1rpx solid rgba(0,0,0,0.05);
  flex-shrink: 0;
  padding-top: var(--status-bar-height, 0px);
}
.header-left { display: flex; align-items: center; gap: 12rpx; }
.header-logo {
  width: 48rpx; height: 48rpx;
  border-radius: 12rpx;
  background: #f2f2f7;
  display: flex; align-items: center; justify-content: center;
}
.header-title { font-size: 30rpx; font-weight: 700; color: rgba(0,0,0,0.85); }
.header-right { display: flex; align-items: center; gap: 16rpx; }

.ws-badge {
  display: flex; align-items: center; gap: 6rpx;
  padding: 6rpx 14rpx;
  border-radius: 100rpx;
  background: rgba(0,0,0,0.04);
}
.ws-badge.connected .ws-dot { background: #52c41a; }
.ws-badge.disconnected .ws-dot { background: #ff4d4f; }
.ws-dot { width: 10rpx; height: 10rpx; border-radius: 50%; }
.ws-text { font-size: 20rpx; color: rgba(0,0,0,0.5); }

.user-btn { padding: 6rpx; }
.user-avatar {
  width: 40rpx; height: 40rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4d4f, #d9363e);
  display: flex; align-items: center; justify-content: center;
}
.user-avatar-text { font-size: 20rpx; font-weight: 700; color: #fff; }

/* ===== Panel ===== */
.panel-mask { position: fixed; inset: 0; z-index: 100; background: rgba(0,0,0,0.15); }
.user-panel {
  position: fixed;
  top: calc(88rpx + var(--status-bar-height, 0px));
  right: 16rpx;
  width: 480rpx;
  max-height: 70vh;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 8rpx 32rpx rgba(0,0,0,0.12);
  z-index: 101;
}
.panel-user-head {
  display: flex; align-items: center; gap: 16rpx;
  padding: 24rpx 24rpx 20rpx;
  border-bottom: 1rpx solid #f0f0f0;
}
.panel-avatar {
  width: 56rpx; height: 56rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4d4f, #d9363e);
  display: flex; align-items: center; justify-content: center;
}
.panel-avatar-text { font-size: 24rpx; font-weight: 700; color: #fff; }
.panel-user-info { display: flex; flex-direction: column; gap: 4rpx; }
.panel-username { font-size: 26rpx; font-weight: 600; color: #1a1a1a; }
.panel-userid { font-size: 20rpx; color: #999; }

.panel-section { padding: 20rpx 24rpx 0; }
.panel-section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12rpx; }
.panel-section-title { font-size: 18rpx; font-weight: 600; color: #999; letter-spacing: 1rpx; display: block; margin-bottom: 12rpx; }
.panel-refresh { font-size: 20rpx; color: #1677ff; margin-bottom: 12rpx; }
.token-box {
  background: #f6f6f6;
  border-radius: 12rpx;
  padding: 12rpx 16rpx;
  margin-bottom: 12rpx;
  max-height: 100rpx;
  overflow: hidden;
}
.token-text { font-size: 18rpx; font-family: monospace; color: #333; word-break: break-all; line-height: 1.5; }
.copy-btn { background: rgba(0,0,0,0.05); border-radius: 10rpx; padding: 12rpx; text-align: center; margin-bottom: 20rpx; }
.copy-btn-text { font-size: 22rpx; color: rgba(0,0,0,0.6); }

.claw-empty { padding: 16rpx; background: #fafafa; border-radius: 12rpx; text-align: center; margin-bottom: 16rpx; }
.claw-empty-text { font-size: 22rpx; color: #bbb; }
.claw-item { padding: 14rpx 18rpx; background: #f8fffe; border: 1rpx solid #d9f7be; border-radius: 12rpx; margin-bottom: 10rpx; }
.claw-item-head { display: flex; align-items: center; gap: 10rpx; margin-bottom: 4rpx; }
.claw-online-dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: #52c41a; }
.claw-id { font-size: 22rpx; font-weight: 600; color: #1a1a1a; }
.claw-heartbeat { font-size: 18rpx; color: #aaa; }

.logout-btn { margin: 20rpx 24rpx 24rpx; padding: 18rpx; background: rgba(255,77,79,0.06); border-radius: 12rpx; text-align: center; }
.logout-btn-text { font-size: 24rpx; color: #ff4d4f; font-weight: 500; }

/* ===== Toolbar ===== */
.toolbar {
  display: flex;
  align-items: center;
  padding: 10rpx 20rpx;
  background: #fafafa;
  border-bottom: 1rpx solid rgba(0,0,0,0.04);
  flex-shrink: 0;
  gap: 12rpx;
}
.device-scroll { flex: 1; }
.device-list { display: flex; gap: 10rpx; align-items: center; white-space: nowrap; }
.device-item {
  display: inline-flex; align-items: center; gap: 8rpx;
  padding: 10rpx 18rpx;
  border-radius: 100rpx;
  background: rgba(0,0,0,0.04);
  flex-shrink: 0;
}
.device-item.active { background: rgba(255,77,79,0.1); }
.device-item.device-empty { opacity: 0.5; }
.device-dot { width: 10rpx; height: 10rpx; border-radius: 50%; background: #52c41a; }
.device-item-text { font-size: 22rpx; color: rgba(0,0,0,0.6); }
.device-item.active .device-item-text { color: #ff4d4f; font-weight: 600; }
.new-session-btn { padding: 10rpx 20rpx; border-radius: 100rpx; background: rgba(0,0,0,0.06); flex-shrink: 0; }
.new-session-text { font-size: 22rpx; color: rgba(0,0,0,0.5); white-space: nowrap; }

/* ===== Chat Scroll ===== */
.chat-scroll { flex: 1; overflow: hidden; }

/* ===== Welcome ===== */
.welcome-wrap { display: flex; flex-direction: column; align-items: center; padding: 48rpx 32rpx 32rpx; }
.welcome-icon-wrap {
  width: 96rpx; height: 96rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #fff0f0, #fff5f5);
  display: flex; align-items: center; justify-content: center;
  margin-bottom: 24rpx;
}
.welcome-icon { width: 56rpx; height: 56rpx; }
.welcome-title { font-size: 34rpx; font-weight: 600; color: rgba(0,0,0,0.85); margin-bottom: 8rpx; }
.welcome-desc { font-size: 24rpx; color: rgba(0,0,0,0.4); margin-bottom: 40rpx; }
.prompt-cards { display: grid; grid-template-columns: 1fr 1fr; gap: 16rpx; width: 100%; }
.prompt-card { padding: 20rpx 18rpx; background: linear-gradient(135deg, #e5f4ff, #efe7ff); border-radius: 16rpx; }
.prompt-card-text { font-size: 22rpx; color: rgba(0,0,0,0.65); line-height: 1.4; }

/* ===== Messages ===== */
.msg-list { padding: 20rpx 20rpx 0; }
.msg-row { display: flex; align-items: flex-end; gap: 12rpx; margin-bottom: 20rpx; }
.msg-row-user { flex-direction: row-reverse; }
.msg-row-assistant { flex-direction: row; }

.msg-avatar { width: 52rpx; height: 52rpx; border-radius: 50%; flex-shrink: 0; display: flex; align-items: center; justify-content: center; }
.assistant-avatar { background: #f2f2f7; border: 1rpx solid rgba(0,0,0,0.05); }
.user-avatar-msg { background: linear-gradient(135deg, #ff4d4f, #d9363e); }
.user-avatar-msg-text { font-size: 22rpx; font-weight: 700; color: #fff; }

.msg-bubble-wrap { display: flex; flex-direction: column; max-width: 80%; gap: 6rpx; }
.bubble-wrap-user { align-items: flex-end; }
.bubble-wrap-assistant { align-items: flex-start; }

.attachment-row { display: flex; flex-wrap: wrap; gap: 6rpx; justify-content: flex-end; }
.attachment-img { width: 120rpx; height: 120rpx; border-radius: 10rpx; object-fit: cover; }

.msg-bubble { padding: 16rpx 20rpx; border-radius: 18rpx; word-break: break-word; line-height: 1.55; }
.bubble-user { background: rgba(0,0,0,0.84); border-bottom-right-radius: 6rpx; }
.bubble-assistant { background: #f5f5f5; border-bottom-left-radius: 6rpx; }
.bubble-text-user { font-size: 26rpx; color: #fff; line-height: 1.55; }

.typing-dots { display: flex; align-items: center; gap: 6rpx; padding: 6rpx 4rpx; }
.dot { width: 8rpx; height: 8rpx; border-radius: 50%; background: rgba(0,0,0,0.3); animation: typingDot 1s ease-in-out infinite; }
.dot:nth-child(2) { animation-delay: 0.15s; }
.dot:nth-child(3) { animation-delay: 0.3s; }
@keyframes typingDot { 0%, 60%, 100% { transform: translateY(0); opacity: 0.4; } 30% { transform: translateY(-6rpx); opacity: 1; } }

.bubble-md { font-size: 26rpx; color: rgba(0,0,0,0.85); line-height: 1.55; }

/* ===== Input Area ===== */
.input-area { flex-shrink: 0; background: #fff; border-top: 1rpx solid rgba(0,0,0,0.05); padding: 12rpx 20rpx 20rpx; }
.attachment-preview { margin-bottom: 10rpx; }
.attachment-preview-list { display: flex; gap: 12rpx; padding: 4rpx 0; }
.att-thumb-wrap { position: relative; flex-shrink: 0; }
.att-thumb { width: 96rpx; height: 96rpx; border-radius: 12rpx; object-fit: cover; border: 1rpx solid rgba(0,0,0,0.08); }
.att-remove { position: absolute; top: -8rpx; right: -8rpx; width: 32rpx; height: 32rpx; border-radius: 50%; background: rgba(0,0,0,0.55); display: flex; align-items: center; justify-content: center; }
.att-remove-text { font-size: 22rpx; color: #fff; line-height: 1; }

.chip-scroll { margin-bottom: 10rpx; }
.chip-list { display: flex; gap: 10rpx; }
.chip { padding: 8rpx 18rpx; background: rgba(0,0,0,0.04); border-radius: 100rpx; flex-shrink: 0; }
.chip-text { font-size: 22rpx; color: rgba(0,0,0,0.5); white-space: nowrap; }

.input-row {
  display: flex; align-items: flex-end; gap: 12rpx;
  background: rgba(0,0,0,0.03);
  border: 1rpx solid rgba(0,0,0,0.06);
  border-radius: 20rpx;
  padding: 10rpx 10rpx 10rpx 16rpx;
  min-height: 76rpx;
}
.input-action-btn { width: 52rpx; height: 52rpx; border-radius: 50%; background: rgba(0,0,0,0.06); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.input-action-icon { font-size: 32rpx; color: rgba(0,0,0,0.5); line-height: 1; }
.input-box { flex: 1; font-size: 26rpx; color: rgba(0,0,0,0.85); background: transparent; min-height: 48rpx; max-height: 160rpx; line-height: 1.5; padding: 6rpx 0; }
.input-placeholder { color: rgba(0,0,0,0.25); }
.send-btn { width: 52rpx; height: 52rpx; border-radius: 50%; background: rgba(0,0,0,0.15); display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.send-btn-active { background: rgba(0,0,0,0.84); }
.send-icon { font-size: 28rpx; color: #fff; line-height: 1; font-weight: 700; }
.send-spinner { width: 28rpx; height: 28rpx; border: 3rpx solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.8s linear infinite; }

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>
