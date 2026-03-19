<template>
  <div :style="layoutStyles.layout">
    <!-- ==================== 主内容区 ==================== -->
    <div :style="chatStyles.chatArea">
      <!-- 顶部导航栏 -->
      <div :style="layoutStyles.header">
        <div :style="layoutStyles.headerLeft">
          <!-- Logo -->
          <img
            src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
            alt="logo"
            width="24"
            height="24"
            draggable="false"
          />
          <span style="font-weight: 600; font-size: 14px">OPENHSD</span>
        </div>

        <div :style="layoutStyles.headerRight">
          <Select
            v-model:value="selectedClawId"
            :options="clawOptions"
            placeholder="选择设备"
            style="width: 200px"
            size="small"
            @change="onClawChange"
          >
            <template #notFoundContent>
              <span style="color: #999; padding: 8px">暂无在线设备</span>
            </template>
          </Select>
          <Badge :status="isConnected ? 'success' : 'error'" :text="isConnected ? '已连接' : '未连接'" />
          <span style="color: #999; font-size: 12px">版本 2026.3.6</span>

          <!-- 用户信息按钮 -->
          <div style="position: relative">
            <Tooltip title="账号信息">
              <div
                @click="onToggleUserPanel"
                style="
                  display: flex;
                  align-items: center;
                  gap: 6px;
                  padding: 4px 10px;
                  border-radius: 6px;
                  cursor: pointer;
                  border: 1px solid rgba(0,0,0,0.1);
                  background: rgba(0,0,0,0.02);
                  transition: all 0.2s;
                "
              >
                <UserOutlined style="font-size: 13px; color: #666" />
                <span style="font-size: 12px; color: #555; max-width: 80px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                  {{ userStore.user?.username || 'user' }}
                </span>
              </div>
            </Tooltip>

            <!-- 下拉面板 -->
            <div
              v-if="showUserPanel"
              style="
                position: absolute;
                top: calc(100% + 8px);
                right: 0;
                width: 320px;
                background: #fff;
                border-radius: 12px;
                box-shadow: 0 4px 24px rgba(0,0,0,0.12), 0 0 0 1px rgba(0,0,0,0.06);
                z-index: 1000;
                overflow: hidden;
              "
            >
              <!-- 面板头部 -->
              <div style="padding: 16px 16px 12px; border-bottom: 1px solid #f0f0f0">
                <div style="display: flex; align-items: center; gap: 10px">
                  <div style="width: 36px; height: 36px; border-radius: 50%; background: linear-gradient(135deg, #ff4d4f, #d9363e); display: flex; align-items: center; justify-content: center">
                    <UserOutlined style="color: white; font-size: 16px" />
                  </div>
                  <div>
                    <div style="font-size: 14px; font-weight: 600; color: #1a1a1a">{{ userStore.user.value?.username }}</div>
                    <div style="font-size: 11px; color: #999">userId: {{ userStore.user.value?.userId }}</div>
                  </div>
                </div>
              </div>

              <!-- Token 区域 -->
              <div style="padding: 14px 16px 0">
                <div style="font-size: 11px; font-weight: 600; color: #999; text-transform: uppercase; letter-spacing: 0.5px; margin-bottom: 8px">
                  连接 Token
                </div>
                <div style="background: #f6f6f6; border-radius: 8px; padding: 10px 12px; font-size: 11px; font-family: monospace; color: #333; word-break: break-all; line-height: 1.6; max-height: 72px; overflow-y: auto; border: 1px solid #eee;">
                  {{ userStore.token }}
                </div>
                <div style="display: flex; gap: 8px; margin-top: 8px">
                  <Button size="small" block :type="tokenCopied ? 'primary' : 'default'" @click="copyToken" style="font-size: 12px; border-radius: 6px">
                    <template #icon><CopyOutlined /></template>
                    {{ tokenCopied ? '已复制！' : '复制 Token' }}
                  </Button>
                </div>
                <div style="margin-top: 8px; padding: 7px 10px; background: #fff7e6; border-radius: 6px; border: 1px solid #ffd591">
                  <div style="font-size: 11px; color: #d46b08; line-height: 1.6">
                    将此 Token 填入插件 <code style="background: #ffe7ba; padding: 1px 4px; border-radius: 3px">cj.config.json</code> → <code style="background: #ffe7ba; padding: 1px 4px; border-radius: 3px">cloud.token</code>
                  </div>
                </div>
              </div>

              <!-- 在线机器列表 -->
              <div style="padding: 12px 16px 0">
                <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px">
                  <span style="font-size: 11px; font-weight: 600; color: #999; text-transform: uppercase; letter-spacing: 0.5px">
                    在线机器
                  </span>
                  <span
                    @click="fetchClawStatus"
                    style="font-size: 11px; color: #1677ff; cursor: pointer"
                  >
                    {{ loadingStatus ? '刷新中...' : '刷新' }}
                  </span>
                </div>

                <!-- 无机器 -->
                <div v-if="clawList.length === 0" style="padding: 12px; background: #fafafa; border-radius: 8px; border: 1px dashed #e0e0e0; text-align: center">
                  <div style="font-size: 12px; color: #bbb">暂无在线插件</div>
                  <div style="font-size: 11px; color: #ccc; margin-top: 2px">请启动 openHSD 插件并填入 Token</div>
                </div>

                <!-- 机器列表 -->
                <div v-for="claw in clawList" :key="claw.clawId" style="
                  padding: 10px 12px;
                  background: #f8fffe;
                  border: 1px solid #d9f7be;
                  border-radius: 8px;
                  margin-bottom: 6px;
                ">
                  <div style="display: flex; align-items: center; gap: 6px; margin-bottom: 4px">
                    <span style="width: 7px; height: 7px; border-radius: 50%; background: #52c41a; flex-shrink: 0; box-shadow: 0 0 0 2px rgba(82,196,26,0.2)"></span>
                    <span style="font-size: 12px; font-weight: 600; color: #1a1a1a; font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                      {{ claw.clawId }}
                    </span>
                  </div>
                  <div v-if="claw.openClawDeviceId" style="font-size: 10px; color: #888; font-family: monospace; overflow: hidden; text-overflow: ellipsis; white-space: nowrap">
                    OpenClaw: {{ claw.openClawDeviceId.substring(0, 24) }}...
                  </div>
                  <div style="font-size: 10px; color: #aaa; margin-top: 2px">
                    最后心跳：{{ formatHeartbeat(claw.lastHeartbeat) }}
                  </div>
                </div>
              </div>

              <!-- 退出登录 -->
              <div style="padding: 12px 16px 14px">
                <Button danger block size="small" @click="onLogout" style="font-size: 12px; border-radius: 6px">
                  退出登录
                </Button>
              </div>
            </div>

            <!-- 点击外部关闭 -->
            <div
              v-if="showUserPanel"
              style="position: fixed; inset: 0; z-index: 999"
              @click="showUserPanel = false; "
            />
          </div>
        </div>
      </div>

      <!-- 聊天标题区 -->
      <div style="padding: 20px 24px 0">
        <div style="display: flex; justify-content: space-between; align-items: flex-start">
          <div>
            <h2 style="margin: 0 0 4px; font-size: 22px; font-weight: 600">
              聊天
            </h2>
            <p style="margin: 0; color: #999; font-size: 13px">
              用于快速干预的直接网关聊天会话。
            </p>
          </div>
          <div style="display: flex; align-items: center; gap: 8px">
            <Select
              v-model:value="currentSession"
              style="width: 160px"
              :options="[
                { value: 'Main Session', label: 'Main Session' },
                { value: 'Session 2', label: 'Session 2' },
              ]"
              size="small"
            />
            <Tooltip title="刷新">
              <Button size="small" type="text">
                <template #icon><SyncOutlined /></template>
              </Button>
            </Tooltip>
          </div>
        </div>
      </div>

      <!-- 聊天内容 -->
      <div :style="chatStyles.chatContent">
        <!-- 消息列表 / 欢迎界面 -->
        <div ref="chatListRef" :style="chatStyles.chatList">
          <template v-if="messages.length > 0">
            <Bubble.List
              :items="bubbleItems"
              :roles="bubbleRoles"
              :style="{
                height: '100%',
                paddingInline: '24px',
                maxWidth: '900px',
                margin: '0 auto',
              }"
            />
          </template>
          <template v-else>
            <div
              style="
                max-width: 800px;
                margin: 0 auto;
                padding: 32px 24px 0;
              "
            >
              <Welcome
                variant="borderless"
                icon="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*s5sNRo5LjfQAAAAAAAAAAAAADgCCAQ/fmt.webp"
                title="你好，我是 AI 助手"
                description="基于 Ant Design X Vue 构建的智能对话界面，为您提供更好的 AI 交互体验~"
              />
              <div style="margin-top: 24px; display: flex; gap: 16px">
                <Prompts
                  :items="[HOT_TOPICS]"
                  :styles="{
                    list: { height: '100%' },
                    item: {
                      flex: 1,
                      backgroundImage:
                        'linear-gradient(123deg, #e5f4ff 0%, #efe7ff 100%)',
                      borderRadius: '12px',
                      border: 'none',
                    },
                    subItem: { padding: 0, background: 'transparent' },
                  }"
                  @item-click="onPromptClick"
                  style="flex: 1"
                />
                <Prompts
                  :items="[DESIGN_GUIDE]"
                  :styles="{
                    item: {
                      flex: 1,
                      backgroundImage:
                        'linear-gradient(123deg, #e5f4ff 0%, #efe7ff 100%)',
                      borderRadius: '12px',
                      border: 'none',
                    },
                    subItem: { background: '#ffffffa6' },
                  }"
                  @item-click="onPromptClick"
                  style="flex: 1"
                />
              </div>
            </div>
          </template>
        </div>

        <!-- 输入区域 -->
        <div :style="chatStyles.senderArea">
          <!-- 快捷提示 -->
          <Prompts
            :items="SENDER_PROMPTS"
            @item-click="onPromptClick"
            :styles="{ item: { padding: '6px 12px' } }"
            :style="chatStyles.senderPrompt"
          />
          <!-- 隐藏的文件选择 input -->
          <input
            ref="fileInputRef"
            type="file"
            multiple
            accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.md,.csv,.zip,.gz,.json,image/*"
            style="display: none"
            @change="onFileInputChange"
          />
          <!-- 输入框 -->
          <Sender
            :value="inputValue"
            @update:value="(val) => (inputValue = val)"
            @submit="onSubmit"
            @cancel="loading = false"
            :loading="loading"
            :style="chatStyles.sender"
            placeholder="Message (Enter to send, Shift+Enter for line breaks, paste images/files)"
            :header="attachments.length > 0 ? renderAttachmentHeader() : undefined"
            :onPasteFile="onPasteFile"
          >
            <template #prefix>
              <Tooltip title="上传文件（PDF、Word、Excel、Markdown 等，最大 50MB）">
                <Button
                  type="text"
                  size="small"
                  style="color: #8c8c8c; padding: 0 4px"
                  @click="triggerFileSelect"
                >
                  <template #icon><PaperClipOutlined style="font-size: 16px" /></template>
                </Button>
              </Tooltip>
            </template>
          </Sender>
          <!-- 底部操作区 -->
          <div
            :style="{
              maxWidth: '800px',
              margin: '8px auto 0',
              display: 'flex',
              justifyContent: 'flex-end',
              gap: '8px',
            }"
          >
            <Button @click="onCreateConversation">New session</Button>
            <Button type="primary" danger @click="() => onSubmit(inputValue)">
              Send
            </Button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>


<script setup>
import { ref, computed, h, watch, nextTick } from 'vue'
import { marked } from 'marked'
import {
  Bubble,
  Conversations,
  Prompts,
  Sender,
  Welcome,
  Attachments,
} from 'ant-design-x-vue'
import {
  Button,
  Avatar,
  Space,
  Flex,
  message,
  Spin,
  theme,
  Badge,
  Select,
  Tooltip,
  Image,
} from 'ant-design-vue'
import {
  PlusOutlined,
  PaperClipOutlined,
  QuestionCircleOutlined,
  EditOutlined,
  DeleteOutlined,
  ShareAltOutlined,
  EllipsisOutlined,
  ScheduleOutlined,
  AppstoreOutlined,
  FileSearchOutlined,
  AppstoreAddOutlined,
  HeartOutlined,
  SmileOutlined,
  ReloadOutlined,
  CopyOutlined,
  LikeOutlined,
  DislikeOutlined,
  CloudUploadOutlined,
  SettingOutlined,
  SyncOutlined,
  UserOutlined,
  DesktopOutlined,
  ApiOutlined,
} from '@ant-design/icons-vue'
import { loading, messages, sendMessage, isConnected, connect, currentClawId, selectClaw, clearHistory } from '../api/aiService.js'
import { uploadFiles, validateFile, formatSize } from '../api/fileService.js'
import { useUserStore } from '../stores/user.js'
import { useRouter } from 'vue-router'
import { onMounted } from 'vue'
import { request } from '../api/request.js'

const userStore = useUserStore()
const router = useRouter()

// ==================== State ====================
const inputValue = ref('')
const chatListRef = ref(null)
const attachmentsOpen = ref(false)
const attachedFiles = ref([])
const currentSession = ref('Main Session')
const showUserPanel = ref(false)
const tokenCopied = ref(false)
const clawList = ref([])
const loadingStatus = ref(false)
const selectedClawId = ref(null)
const attachments = ref([])

// 页面挂载后建立 WS 连接并加载设备列表
onMounted(async () => {
  connect()
  await fetchClawStatus()
  if (clawList.value.length > 0) {
    selectedClawId.value = clawList.value[0].clawId
    await selectClaw(clawList.value[0].clawId)
  }
})

// 滚动到底部的通用方法
const scrollToBottom = () => {
  nextTick(() => {
    setTimeout(() => {
      if (!chatListRef.value) return
      
      // 尝试找到 Bubble.List 内部的滚动容器
      const selectors = [
        '.ant-x-bubble-list',
        '.ant-bubble-list',
        '[class*="bubble-list"]',
        '[style*="overflow"]'
      ]
      
      let scrollContainer = null
      for (const sel of selectors) {
        const el = chatListRef.value.querySelector(sel)
        if (el && el.scrollHeight > el.clientHeight) {
          scrollContainer = el
          break
        }
      }
      
      // 如果没找到内部容器，使用 chatListRef 本身
      if (!scrollContainer) {
        scrollContainer = chatListRef.value
      }
      
      scrollContainer.scrollTo({
        top: scrollContainer.scrollHeight,
        behavior: 'smooth'
      })
    }, 50)
  })
}

// 监听消息变化，自动滚动到底部
watch(messages, () => {
  scrollToBottom()
}, { deep: true, flush: 'post' })

// ==================== 用户操作 ====================
const copyToken = () => {
  // computed ref 在 script 里需要 .value
  navigator.clipboard.writeText(userStore.token.value).then(() => {
    tokenCopied.value = true
    setTimeout(() => { tokenCopied.value = false }, 2000)
  })
}

const onLogout = () => {
  userStore.logout()
  router.push('/login')
}

// 查询在线机器列表
const fetchClawStatus = async () => {
  const userId = userStore.user.value?.userId
  if (!userId) return
  loadingStatus.value = true
  try {
    const res = await request(`/claw/status?userId=${userId}`)
    const data = await res.json()
    clawList.value = data.clawList || []
  } catch (e) {
    console.error('[ChatView] 查询机器状态失败：', e)
  } finally {
    loadingStatus.value = false
  }
}

// 切换设备
const onClawChange = async (clawId) => {
  selectedClawId.value = clawId
  await selectClaw(clawId)
}

// 设备选项（2台以上在线时显示「全部设备」）
const clawOptions = computed(() => {
  const options = clawList.value.map(c => ({
    value: c.clawId,
    label: c.clawId
  }))
  if (options.length > 1) {
    options.unshift({ value: '__ALL__', label: '全部设备' })
  }
  return options
})

// 打开面板时自动刷新机器列表
const onToggleUserPanel = () => {
  showUserPanel.value = !showUserPanel.value
  if (showUserPanel.value) fetchClawStatus()
}

// 格式化最后心跳时间
const formatHeartbeat = (ts) => {
  if (!ts) return '未知'
  const diff = Date.now() - ts
  if (diff < 60000) return `${Math.floor(diff / 1000)}s 前`
  return `${Math.floor(diff / 60000)}min 前`
}

// ==================== Theme ====================
const { token } = theme.useToken()

// ==================== Styles ====================
const layoutStyles = computed(() => ({
  layout: {
    width: '100%',
    height: '100vh',
    display: 'flex',
    background: token.value.colorBgContainer,
    fontFamily: `${token.value.fontFamily}, sans-serif`,
    overflow: 'hidden',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '0 24px',
    height: '56px',
    borderBottom: `1px solid ${token.value.colorBorderSecondary}`,
    background: token.value.colorBgContainer,
  },
  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
  },
  headerRight: {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  },
}))

const chatStyles = computed(() => ({
  chatArea: {
    height: '100%',
    width: '100%',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  chatContent: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    padding: '0',
    overflow: 'hidden',
  },
  chatList: {
    flex: 1,
    overflow: 'auto',
    padding: '24px 0',
  },
  senderArea: {
    padding: '0 24px 24px',
  },
  sender: {
    width: '100%',
    maxWidth: '800px',
    margin: '0 auto',
  },
  senderPrompt: {
    width: '100%',
    maxWidth: '800px',
    margin: '0 auto 8px',
  },
}))

const messageStyles = computed(() => ({
  loadingMessage: {
    backgroundImage:
      'linear-gradient(90deg, #ff6b23 0%, #af3cb8 31%, #53b6ff 89%)',
    backgroundSize: '100% 2px',
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'bottom',
  },
}))

// ==================== Prompts Data ====================
const HOT_TOPICS = {
  key: '1',
  label: '热门话题',
  children: [
    {
      key: '1-1',
      description: '这个项目是做什么的？',
    },
    {
      key: '1-2',
      description: '如何使用这个对话界面？',
    },
    {
      key: '1-3',
      description: '有哪些技术特点？',
    },
    {
      key: '1-4',
      description: '如何快速开始开发？',
    },
  ],
}

const DESIGN_GUIDE = {
  key: '2',
  label: '设计指南',
  children: [
    {
      key: '2-1',
      label: '意图',
      description: 'AI 理解用户需求并提供解决方案。',
    },
    {
      key: '2-2',
      label: '角色',
      description: 'AI 的公共形象和个性',
    },
    {
      key: '2-3',
      label: '对话',
      description: 'AI 以用户理解的方式表达自己',
    },
    {
      key: '2-4',
      label: '界面',
      description: 'AI 平衡"对话"和"行动"行为。',
    },
  ],
}

const SENDER_PROMPTS = [
  { key: '1', description: '项目介绍' },
  { key: '2', description: '功能演示' },
  { key: '3', description: '使用指南' },
  { key: '4', description: '技术支持' },
]

// ==================== Methods ====================
const onSubmit = (val) => {
  if (!val && attachments.value.length === 0) return
  if (loading.value) {
    message.error('请求正在进行中，请稍候...')
    return
  }
  sendMessage(val, attachments.value, clawList.value)
  inputValue.value = ''
  attachments.value = []
}

// ==================== 附件处理 ====================

/** 文件上传中的 uid 集合（用于显示 loading 状态） */
const uploadingUids = ref(new Set())

/**
 * 将 File 对象添加为附件并上传
 * 图片：先本地预览，上传后保留本地预览 + 存储 objectKey
 * 其他文件：直接上传，显示文件卡片
 */
const addAndUploadFiles = async (files) => {
  const userId = userStore.user.value?.userId
  const clawId = currentClawId.value || 'upload'
  if (!userId) return

  const fileArray = Array.from(files)

  for (const file of fileArray) {
    const check = validateFile(file)
    if (!check.valid) {
      message.error(check.reason)
      continue
    }

    const uid = `${Date.now()}-${Math.random().toString(36).slice(2)}`
    const isImage = file.type.startsWith('image/')

    // 先插入占位（图片显示本地预览，文件显示 loading 状态）
    let localBase64 = null
    if (isImage) {
      // 图片：用 Promise 等待 FileReader 完成后再继续
      localBase64 = await new Promise((resolve) => {
        const reader = new FileReader()
        reader.onload = (e) => resolve(e.target.result)
        reader.readAsDataURL(file)
      })
      attachments.value.push({
        uid,
        name:      file.name,
        type:      file.type,
        size:      file.size,
        base64:    localBase64,  // 保留本地预览（Data URL）
        uploading: true,
      })
    } else {
      attachments.value.push({
        uid,
        name:      file.name,
        type:      file.type,
        size:      file.size,
        base64:    null,
        uploading: true,
      })
    }

    uploadingUids.value.add(uid)

    // 上传到 MinIO
    try {
      const results = await uploadFiles([file], String(userId), clawId)
      const uploaded = results[0]
      console.log('[ChatView] 上传完成，附件信息：', JSON.stringify(uploaded))
      // 更新附件：添加 objectKey + url，去除 uploading 状态
      const idx = attachments.value.findIndex(a => a.uid === uid)
      if (idx >= 0) {
        attachments.value[idx] = {
          uid,
          name:      uploaded.name || file.name,
          // 保留前端原始 type，后端返回的 type 可能不正确
          type:      file.type || uploaded.type || 'application/octet-stream',
          size:      uploaded.size || file.size,
          objectKey: uploaded.objectKey,
          url:       uploaded.url,
          // 图片：保留本地 base64 预览（不替换成 MinIO URL）
          base64:    isImage ? localBase64 : null,
          uploading: false,
        }
      }
    } catch (e) {
      message.error(`${file.name} 上传失败：${e.message}`)
      // 上传失败时移除该附件
      attachments.value = attachments.value.filter(a => a.uid !== uid)
    } finally {
      uploadingUids.value.delete(uid)
    }
  }
}

// onPasteFile(firstFile, fileList) — ant-design-x-vue Sender 的回调签名
const onPasteFile = (firstFile, fileList) => {
  const files = fileList ?? [firstFile]
  addAndUploadFiles(Array.from(files))
}

const removeAttachment = (uid) => {
  attachments.value = attachments.value.filter(a => a.uid !== uid)
}

/** 触发文件选择对话框 */
const fileInputRef = ref(null)
const triggerFileSelect = () => {
  fileInputRef.value?.click()
}
const onFileInputChange = (e) => {
  const files = e.target.files
  if (files && files.length > 0) {
    addAndUploadFiles(files)
  }
  // 清空 input，允许重复选同一文件
  e.target.value = ''
}

/**
 * 渲染附件预览区（Sender 的 header slot）
 * 图片：缩略图 + 删除按钮
 * 文件：文件卡片（图标 + 文件名 + 大小）+ 删除按钮
 */
const renderAttachmentHeader = () => {
  return h('div', {
    style: {
      display: 'flex',
      flexWrap: 'wrap',
      gap: '8px',
      padding: '8px 12px',
      background: '#fafafa',
      borderRadius: '8px',
      marginBottom: '8px',
    }
  }, attachments.value.map(att => {
    // 判断是否是图片：type 以 image/ 开头，或者有 base64 DataURL
    const isImage = (att.type && att.type.startsWith('image/')) || 
                    (att.base64 && att.base64.startsWith('data:image'))
    const isUploading = att.uploading

    // 删除按钮（右上角）
    const removeBtn = h('button', {
      onClick: (e) => { e.stopPropagation(); removeAttachment(att.uid) },
      style: {
        position: 'absolute',
        top: '3px', right: '3px',
        width: '18px', height: '18px',
        borderRadius: '50%',
        border: 'none',
        background: 'rgba(0,0,0,0.55)',
        color: '#fff',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: '11px',
        zIndex: 10,
        padding: 0,
      }
    }, '×')

    if (isImage) {
      // 图片缩略图卡片
      return h('div', {
        key: att.uid,
        style: {
          position: 'relative',
          width: '80px', height: '80px',
          borderRadius: '8px',
          overflow: 'hidden',
          border: '1px solid #e8e8e8',
          flexShrink: 0,
          opacity: isUploading ? 0.6 : 1,
          transition: 'opacity 0.2s',
        }
      }, [
        h(Image, {
          src: att.base64,
          width: 80, height: 80,
          style: { objectFit: 'cover', display: 'block' },
          preview: att.base64 ? { src: att.base64 } : false,
        }),
        isUploading && h('div', {
          style: {
            position: 'absolute', inset: 0,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            background: 'rgba(255,255,255,0.6)',
            fontSize: '11px', color: '#666',
          }
        }, '上传中...'),
        !isUploading && removeBtn,
      ])
    } else {
      // 文件卡片
      const fileIcon = getFileIcon(att.type)
      const fileColor = getFileColor(att.type)
      return h('div', {
        key: att.uid,
        style: {
          position: 'relative',
          width: '120px',
          padding: '8px',
          borderRadius: '8px',
          border: '1px solid #e8e8e8',
          background: '#fff',
          flexShrink: 0,
          display: 'flex',
          flexDirection: 'column',
          gap: '4px',
          opacity: isUploading ? 0.6 : 1,
          transition: 'opacity 0.2s',
        }
      }, [
        // 文件图标
        h('div', {
          style: {
            fontSize: '22px',
            color: fileColor,
            lineHeight: 1,
          }
        }, fileIcon),
        // 文件名（截断）
        h('div', {
          style: {
            fontSize: '11px',
            color: '#333',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            maxWidth: '100%',
          },
          title: att.name,
        }, att.name),
        // 文件大小 或 上传中
        h('div', {
          style: { fontSize: '10px', color: '#999' }
        }, isUploading ? '上传中...' : formatSize(att.size || 0)),
        !isUploading && removeBtn,
      ])
    }
  }))
}

/** 根据 MIME 返回文件图标 emoji */
function getFileIcon(mime) {
  if (!mime) return '📄'
  const m = mime.toLowerCase()
  if (m === 'application/pdf') return '📕'
  if (m.includes('word') || m.includes('msword') || m.includes('wordprocessing')) return '📘'
  if (m.includes('excel') || m.includes('spreadsheet')) return '📗'
  if (m.includes('powerpoint') || m.includes('presentation')) return '📙'
  if (m === 'text/markdown') return '📝'
  if (m === 'text/plain') return '📄'
  if (m === 'text/csv') return '📊'
  if (m.includes('zip') || m.includes('gzip') || m.includes('compressed')) return '📦'
  if (m === 'application/json') return '{ }'
  return '📄'
}

function getFileColor(mime) {
  if (!mime) return '#8c8c8c'
  const m = mime.toLowerCase()
  if (m === 'application/pdf') return '#ff4d4f'
  if (m.includes('word') || m.includes('msword') || m.includes('wordprocessing')) return '#1677ff'
  if (m.includes('excel') || m.includes('spreadsheet')) return '#52c41a'
  if (m.includes('powerpoint') || m.includes('presentation')) return '#fa8c16'
  if (m.includes('zip') || m.includes('gzip') || m.includes('compressed')) return '#faad14'
  if (m === 'application/json') return '#722ed1'
  return '#8c8c8c'
}

const onCreateConversation = async () => {
  await clearHistory()
  message.info('已清空对话')
}

const onPromptClick = (info) => {
  onSubmit(info.data.description)
}

// ==================== Bubble items ====================
const bubbleItems = computed(() => {
  return messages.value.map((msg, index) => {
    let content = msg.content || ''

    // 处理附件：图片和文件都用 <!--img:url--> 和 <!--file:...--> 标记，不走 Markdown
    if (msg.attachments && msg.attachments.length > 0) {
      const imageExts = ['jpg','jpeg','png','gif','webp','bmp','svg']
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
        'application/gzip'
      ]
      const docExts = ['pdf','doc','docx','xls','xlsx','ppt','pptx','txt','md','csv','json','zip','gz']
      
      const isImageAtt = a => {
        if (a.type && a.type.toLowerCase().startsWith('image/')) {
          const mimeType = (a.type || '').toLowerCase()
          if (docMimes.some(m => mimeType.includes(m))) return false
          return true
        }
        if (a.base64 && a.base64.startsWith('data:image')) return true
        const src = a.url || a.name || a.objectKey || ''
        const ext = src.split('.').pop().split('?')[0].toLowerCase()
        if (docExts.includes(ext)) return false
        return imageExts.includes(ext)
      }
      const imageAttachments = msg.attachments.filter(isImageAtt)
      const fileAttachments  = msg.attachments.filter(a => !isImageAtt(a))

      // 图片用 <!--img:url--> 标记，避免 Markdown 字符串闪烁
      if (imageAttachments.length > 0) {
        const imgMarkers = imageAttachments
          .map(att => `<!--img:${att.base64 || att.url || ''}-->`)
          .join('')
        content = imgMarkers + (content ? '\n\n' + content : '')
      }

      // 文件用 <!--file:...--> 标记
      if (fileAttachments.length > 0) {
        const fileMarkers = fileAttachments
          .map(att => `<!--file:${encodeURIComponent(att.name)}|${att.type || ''}|${att.size || 0}|${att.url || ''}-->`)
          .join('')
        content = fileMarkers + content
      }
    }

    // 广播模式下，在 content 前面注入设备标识，格式：<!--claw:xxx-->
    if (msg.clawId && msg.role === 'assistant') {
      content = `<!--claw:${msg.clawId}-->${content}`
    }

    // filePush 文件推送
    if (msg.filePush) {
      const f = msg.filePush
      const imageExts = ['jpg','jpeg','png','gif','webp','bmp','svg']
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
        'application/gzip'
      ]
      const docExts = ['pdf','doc','docx','xls','xlsx','ppt','pptx','txt','md','csv','json','zip','gz']
      
      const ext = (f.name || '').split('.').pop().split('?')[0].toLowerCase()
      const mimeType = (f.type || '').toLowerCase()
      
      const isImageByMime = mimeType.startsWith('image/')
      const isImageByExt = imageExts.includes(ext)
      const isDocumentByMime = docMimes.some(m => mimeType.includes(m))
      const isDocumentByExt = docExts.includes(ext)
      
      const isImage = isImageByMime || (isImageByExt && !isDocumentByMime && !isDocumentByExt)
      
      if (isImage) {
        content = `<!--img:${f.url}-->` + content
      } else {
        content = `<!--file:${encodeURIComponent(f.name)}|${f.type || ''}|${f.size || 0}|${f.url || ''}-->` + content
      }
    }

    return {
      key: msg.messageId || index.toString(),
      role: msg.role,
      content,
      loading: msg.loading || false,
    }
  })
})

const bubbleRoles = computed(() => ({
  assistant: {
    placement: 'start',
    typing: { step: 5, interval: 20 },
    messageRender: (content) => {
      if (!content) return null

      const children = []
      let actualContent = content

      // 1. 解析图片标记 <!--img:url-->（无闪烁）
      const imgTagRegex = /<!--img:([^>]*)-->/g
      const imgUrls = []
      let imgTagMatch
      while ((imgTagMatch = imgTagRegex.exec(actualContent)) !== null) {
        imgUrls.push(imgTagMatch[1])
      }
      actualContent = actualContent.replace(imgTagRegex, '')
      if (imgUrls.length > 0) {
        children.push(h('div', {
          style: { display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '8px' }
        }, imgUrls.map((src, i) =>
          h(Image, {
            key: i, src,
            width: 120, height: 120,
            style: { borderRadius: '8px', objectFit: 'cover', cursor: 'pointer' },
            preview: { src },
          })
        )))
      }

      // 2. 解析文件标记 <!--file:name|type|size|url-->
      const fileRegex = /<!--file:([^|]*)\|([^|]*)\|([^|]*)\|([^>]*)-->/g
      const fileItems = []
      let fileMatch
      while ((fileMatch = fileRegex.exec(actualContent)) !== null) {
        fileItems.push({
          name: decodeURIComponent(fileMatch[1]),
          type: fileMatch[2],
          size: Number(fileMatch[3]) || 0,
          url:  fileMatch[4],
        })
      }
      actualContent = actualContent.replace(fileRegex, '')
      if (fileItems.length > 0) {
        const fileCards = fileItems.map((f, i) =>
          h('a', {
            key: i,
            href: f.url || '#',
            target: '_blank',
            rel: 'noopener noreferrer',
            style: {
              display: 'flex', alignItems: 'center', gap: '8px',
              padding: '8px 10px', borderRadius: '8px',
              border: '1px solid #e8e8e8', background: '#fafafa',
              textDecoration: 'none', color: 'inherit',
              minWidth: '160px', maxWidth: '260px',
              cursor: f.url ? 'pointer' : 'default',
            }
          }, [
            h('span', { style: { fontSize: '22px', flexShrink: 0, color: getFileColor(f.type) } }, getFileIcon(f.type)),
            h('div', { style: { minWidth: 0 } }, [
              h('div', { style: { fontSize: '12px', color: '#333', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }, title: f.name }, f.name),
              h('div', { style: { fontSize: '10px', color: '#999', marginTop: '2px' } }, formatSize(f.size)),
            ])
          ])
        )
        children.push(h('div', { style: { display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '8px' } }, fileCards))
      }

      // 3. 解析广播模式设备标识 <!--claw:xxx-->
      const clawMatch = actualContent.match(/^<!--claw:([^>]+)-->/)
      if (clawMatch) {
        actualContent = actualContent.replace(clawMatch[0], '')
        children.push(h('div', {
          style: {
            fontSize: '11px', color: '#1677ff', background: '#e6f4ff',
            border: '1px solid #91caff', borderRadius: '4px',
            padding: '1px 6px', marginBottom: '6px',
            display: 'inline-block', fontFamily: 'monospace',
          }
        }, clawMatch[1]))
      }

      // 4. 渲染剩余 Markdown 文本
      if (actualContent.trim()) {
        const html = marked.parse(actualContent.replace(/\r\n/g, '\n'), { breaks: false, gfm: true })
        children.push(h('div', { innerHTML: html, class: 'markdown-body' }))
      }

      return children.length > 0 ? h('div', {}, children) : null
    },
  },
  user: {
    placement: 'end',
    messageRender: (content) => {
      if (!content) return null

      const children = []

      // 1. 解析图片标记 <!--img:url-->
      const imgTagRegex = /<!--img:([^>]*)-->/g
      const imgUrls = []
      let imgTagMatch
      let restContent = content
      while ((imgTagMatch = imgTagRegex.exec(restContent)) !== null) {
        imgUrls.push(imgTagMatch[1])
      }
      restContent = restContent.replace(imgTagRegex, '')
      if (imgUrls.length > 0) {
        children.push(h('div', {
          style: { display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '8px' }
        }, imgUrls.map((src, i) =>
          h(Image, {
            key: i, src,
            width: 120, height: 120,
            style: { borderRadius: '8px', objectFit: 'cover', cursor: 'pointer' },
            preview: { src },
          })
        )))
      }

      // 2. 解析文件标记 <!--file:name|type|size|url-->
      const fileRegex = /<!--file:([^|]*)\|([^|]*)\|([^|]*)\|([^>]*)-->/g
      const fileItems = []
      let fileMatch
      while ((fileMatch = fileRegex.exec(restContent)) !== null) {
        fileItems.push({
          name: decodeURIComponent(fileMatch[1]),
          type: fileMatch[2],
          size: Number(fileMatch[3]) || 0,
          url:  fileMatch[4],
        })
      }
      restContent = restContent.replace(fileRegex, '')

      // 渲染文件卡片
      if (fileItems.length > 0) {
        const fileCards = fileItems.map((f, i) =>
          h('a', {
            key: i,
            href: f.url || '#',
            target: '_blank',
            rel: 'noopener noreferrer',
            style: {
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 10px',
              borderRadius: '8px',
              border: '1px solid #e8e8e8',
              background: '#fafafa',
              textDecoration: 'none',
              color: 'inherit',
              minWidth: '160px',
              maxWidth: '260px',
              cursor: f.url ? 'pointer' : 'default',
            }
          }, [
            h('span', { style: { fontSize: '22px', flexShrink: 0, color: getFileColor(f.type) } }, getFileIcon(f.type)),
            h('div', { style: { minWidth: 0 } }, [
              h('div', {
                style: { fontSize: '12px', color: '#333', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
                title: f.name,
              }, f.name),
              h('div', { style: { fontSize: '10px', color: '#999', marginTop: '2px' } }, formatSize(f.size)),
            ])
          ])
        )
        children.push(h('div', {
          style: { display: 'flex', flexWrap: 'wrap', gap: '6px', marginBottom: '8px' }
        }, fileCards))
      }

      // 2. 分离图片 Markdown 和文本（匹配任意 alt text）
      const imageRegex = /!\[([^\]]*)\]\(([^)]+)\)/g
      const images = []
      let imgMatch
      while ((imgMatch = imageRegex.exec(restContent)) !== null) {
        images.push(imgMatch[2]) // 第2个捕获组是 URL
      }
      const textContent = restContent.replace(imageRegex, '').trim()

      // 渲染图片缩略图
      if (images.length > 0) {
        const imgContainer = h('div', {
          style: {
            display: 'flex', flexWrap: 'wrap', gap: '6px',
            marginBottom: textContent ? '8px' : 0,
          }
        }, images.map((src, i) =>
          h(Image, {
            key: i, src,
            width: 120, height: 120,
            style: { borderRadius: '8px', objectFit: 'cover', cursor: 'pointer' },
            preview: { src },
          })
        ))
        children.push(imgContainer)
      }

      // 渲染文本
      if (textContent) {
        children.push(h('div', textContent))
      }

      return h('div', {}, children)
    },
  },
}))
</script>



<style scoped>
:deep(.ant-welcome) {
  padding: 24px 0;
}

:deep(.ant-prompts-list) {
  flex-wrap: wrap;
}

/* 气泡容器：flex 子元素收缩，不撑破父级 */
:deep(.ant-bubble-content-wrapper) {
  min-width: 0;
  max-width: calc(100% - 40px); /* 40px 为头像+gap 预留 */
}

/* 气泡内容区：只横向隐藏，纵向正常展开 */
:deep(.ant-bubble-content),
:deep(.ant-bubble-content-filled),
:deep(.ant-bubble-content-outlined),
:deep(.ant-bubble-content-borderless) {
  word-break: break-word;
  overflow-wrap: break-word;
  min-width: 0;
  max-width: 100%;
  overflow-x: hidden;
  overflow-y: visible;
  box-sizing: border-box;
}

</style>

<!-- Markdown 样式：非 scoped，因为 innerHTML 注入的子元素没有 data-v 属性 -->
<style>
.markdown-body {
  font-size: 14px;
  line-height: 1.65;
  color: inherit;
  word-break: break-word;
  overflow-wrap: break-word;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}

.markdown-body > *:first-child {
  margin-top: 0;
}
.markdown-body > *:last-child {
  margin-bottom: 0;
}

/* 标题 */
.markdown-body h1 {
  font-size: 1.4em;
  font-weight: 700;
  line-height: 1.3;
  margin: 14px 0 6px;
  color: #1a1a1a;
}

.markdown-body h2 {
  font-size: 1.2em;
  font-weight: 700;
  line-height: 1.3;
  margin: 12px 0 5px;
  color: #1a1a1a;
}

.markdown-body h3 {
  font-size: 1em;
  font-weight: 700;
  line-height: 1.3;
  margin: 10px 0 4px;
  color: #333;
  padding-left: 8px;
  border-left: 3px solid #bbb;
}

.markdown-body h4 {
  font-size: 0.93em;
  font-weight: 600;
  line-height: 1.3;
  margin: 8px 0 3px;
  color: #444;
}

.markdown-body h5 {
  font-size: 0.9em;
  font-weight: 600;
  line-height: 1.3;
  margin: 8px 0 3px;
  color: #444;
}

.markdown-body h6 {
  font-size: 0.85em;
  font-weight: 600;
  line-height: 1.3;
  margin: 8px 0 3px;
  color: #666;
}

/* 段落 */
.markdown-body p {
  margin: 4px 0 6px;
}

/* 加粗 */
.markdown-body strong {
  font-weight: 700;
  color: #111;
}

/* 列表 */
.markdown-body ul,
.markdown-body ol {
  padding-left: 1.6em;
  margin: 4px 0 6px;
  box-sizing: border-box;
}

.markdown-body li {
  margin: 3px 0;
  line-height: 1.6;
  word-break: break-word;
  overflow-wrap: break-word;
}

.markdown-body li > p {
  margin: 0;
}

/* 分隔线 */
.markdown-body hr {
  border: none;
  border-top: 1px solid #ebebeb;
  margin: 8px 0;
}

.markdown-body hr + h1,
.markdown-body hr + h2,
.markdown-body hr + h3,
.markdown-body hr + h4,
.markdown-body hr + h5,
.markdown-body hr + h6 {
  margin-top: 4px;
}

/* 行内代码 */
.markdown-body code {
  background: #f0f0f0;
  padding: 1px 5px;
  border-radius: 3px;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 0.88em;
  color: #d56161;
}

/* 代码块 */
.markdown-body pre {
  background: #f6f8fa;
  padding: 10px 14px;
  border-radius: 6px;
  overflow-x: auto;
  margin: 6px 0;
  border: 1px solid #eaecef;
}

.markdown-body pre code {
  background: none;
  padding: 0;
  font-size: 0.85em;
  color: inherit;
}

/* 引用块 */
.markdown-body blockquote {
  border-left: 3px solid #d0d0d0;
  padding: 2px 0 2px 12px;
  margin: 6px 0;
  color: #666;
}

.markdown-body blockquote p {
  margin: 0;
}

/* 表格 */
.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  max-width: 100%;
  table-layout: fixed;
  margin: 8px 0;
  font-size: 0.93em;
  box-sizing: border-box;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #e0e0e0;
  padding: 5px 10px;
  text-align: left;
  word-break: break-word;
  overflow-wrap: break-word;
  white-space: normal;
}

.markdown-body th {
  background: #f5f5f5;
  font-weight: 600;
  color: #333;
}

.markdown-body tr:nth-child(even) td {
  background: #fafafa;
}

/* 图片 */
.markdown-body img {
  max-width: 100%;
  border-radius: 4px;
}
</style>
