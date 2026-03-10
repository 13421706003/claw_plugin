<template>
  <div :style="layoutStyles.layout">
    <!-- ==================== 侧边栏 ==================== -->
    <div :style="siderStyles.sider">
      <!-- Logo -->
      <div
        style="
          display: flex;
          align-items: center;
          padding: 16px 20px;
          gap: 10px;
        "
      >
        <img
          src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
          alt="logo"
          width="28"
          height="28"
          draggable="false"
        />
        <div>
          <div style="font-weight: bold; font-size: 14px; line-height: 1.2">
            OPENHSD
          </div>
          <div
            style="
              font-size: 10px;
              color: #999;
              text-transform: uppercase;
              letter-spacing: 0.5px;
            "
          >
            GATEWAY DASHBOARD
          </div>
        </div>
      </div>

      <!-- 导航菜单 -->
      <div style="flex: 1; overflow-y: auto; padding: 0 8px">
        <template v-for="section in navSections" :key="section.title">
          <div
            style="
              padding: 12px 12px 4px;
              font-size: 11px;
              color: #999;
              text-transform: uppercase;
              letter-spacing: 0.5px;
              display: flex;
              justify-content: space-between;
              align-items: center;
            "
          >
            <span>{{ section.title }}</span>
            <span style="font-size: 10px; cursor: pointer">-</span>
          </div>
          <div v-for="item in section.items" :key="item.key">
            <div
              :style="{
                display: 'flex',
                alignItems: 'center',
                padding: '8px 12px',
                borderRadius: '6px',
                cursor: 'pointer',
                gap: '8px',
                fontSize: '13px',
                background: item.active ? '#ff4d4f15' : 'transparent',
                color: item.active ? '#ff4d4f' : token.colorText,
                borderLeft: item.active ? '3px solid #ff4d4f' : '3px solid transparent',
                transition: 'all 0.2s',
              }"
            >
              <component :is="item.icon" :style="{ fontSize: '14px' }" />
              <span>{{ item.label }}</span>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- ==================== 主内容区 ==================== -->
    <div :style="chatStyles.chatArea">
      <!-- 顶部导航栏 -->
      <div :style="layoutStyles.header">
        <div :style="layoutStyles.headerLeft">
          <Button
            type="text"
            size="small"
            @click="siderCollapsed = !siderCollapsed"
          >
            <template #icon>
              <MenuFoldOutlined v-if="!siderCollapsed" />
              <MenuUnfoldOutlined v-else />
            </template>
          </Button>
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
        <div :style="chatStyles.chatList">
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
          <!-- 输入框 -->
          <Sender
            :value="inputValue"
            @update:value="(val) => (inputValue = val)"
            @submit="onSubmit"
            @cancel="loading = false"
            :loading="loading"
            :style="chatStyles.sender"
            placeholder="Message (Enter to send, Shift+Enter for line breaks, paste images)"
            :header="attachments.length > 0 ? renderAttachmentHeader() : undefined"
            :onPasteFile="onPasteFile"
          />
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
import { ref, computed, watch, h } from 'vue'
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
  CommentOutlined,
  ReloadOutlined,
  CopyOutlined,
  LikeOutlined,
  DislikeOutlined,
  CloudUploadOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  SettingOutlined,
  SyncOutlined,
  UserOutlined,
  TeamOutlined,
  CodeOutlined,
  ThunderboltOutlined,
  NodeIndexOutlined,
  FileTextOutlined,
  DesktopOutlined,
  BugOutlined,
  ApiOutlined,
  RobotOutlined,
} from '@ant-design/icons-vue'
import { loading, messages, sendMessage, isConnected, connect, currentClawId, selectClaw, clearHistory } from '../api/aiService.js'
import { useUserStore } from '../stores/user.js'
import { useRouter } from 'vue-router'
import { onMounted } from 'vue'

const userStore = useUserStore()
const router = useRouter()

// ==================== State ====================
const inputValue = ref('')
const attachmentsOpen = ref(false)
const attachedFiles = ref([])
const siderCollapsed = ref(false)
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
  // computed ref 在 script 里需要 .value
  const userId = userStore.user.value?.userId
  if (!userId) return
  loadingStatus.value = true
  try {
    const res = await fetch(`/api/claw/status?userId=${userId}`)
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

// 设备选项
const clawOptions = computed(() => 
  clawList.value.map(c => ({
    value: c.clawId,
    label: c.clawId
  }))
)

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
const siderStyles = computed(() => ({
  sider: {
    background: token.value.colorBgLayout,
    width: siderCollapsed.value ? '0px' : '260px',
    minWidth: siderCollapsed.value ? '0px' : '260px',
    height: '100%',
    display: 'flex',
    flexDirection: 'column',
    borderRight: `1px solid ${token.value.colorBorderSecondary}`,
    transition: 'width 0.3s, min-width 0.3s',
    overflow: 'hidden',
  },
}))

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

// ==================== Navigation Items ====================
const navSections = ref([
  {
    title: '聊天',
    items: [
      { key: 'chat', label: '聊天', icon: CommentOutlined, active: true },
    ],
  },
  {
    title: '控制',
    items: [],
  },
  {
    title: '代理',
    items: [
      { key: 'agents', label: '代理', icon: RobotOutlined },
      { key: 'skills', label: '技能', icon: ThunderboltOutlined },
      { key: 'nodes', label: '节点', icon: NodeIndexOutlined },
    ],
  },
  {
    title: '设置',
    items: [
      { key: 'config', label: '配置', icon: SettingOutlined },
      { key: 'debug', label: '调试', icon: BugOutlined },
      { key: 'logs', label: '日志', icon: FileTextOutlined },
    ],
  },
  {
    title: '资源',
    items: [{ key: 'docs', label: '文档', icon: FileTextOutlined }],
  },
])

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
  sendMessage(val, attachments.value)
  inputValue.value = ''
  attachments.value = []
}

// onPasteFile(firstFile, fileList) — ant-design-x-vue Sender 的回调签名
const onPasteFile = (firstFile, fileList) => {
  const files = fileList ?? [firstFile]
  Array.from(files).forEach(file => {
    if (file && file.type.startsWith('image/')) {
      const reader = new FileReader()
      reader.onload = (e) => {
        attachments.value.push({
          uid: Date.now() + '-' + Math.random().toString(36).slice(2),
          name: file.name || 'image.png',
          type: file.type,
          base64: e.target.result,
        })
      }
      reader.readAsDataURL(file)
    }
  })
}

const removeAttachment = (uid) => {
  attachments.value = attachments.value.filter(a => a.uid !== uid)
}

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
  }, attachments.value.map(att =>
    h('div', {
      key: att.uid,
      style: {
        position: 'relative',
        width: '80px',
        height: '80px',
        borderRadius: '8px',
        overflow: 'hidden',
        border: '1px solid #e8e8e8',
        flexShrink: 0,
      }
    }, [
      h(Image, {
        src: att.base64,
        width: 80,
        height: 80,
        style: { objectFit: 'cover', display: 'block' },
        preview: { src: att.base64 },
      }),
      h('button', {
        onClick: (e) => {
          e.stopPropagation()
          removeAttachment(att.uid)
        },
        style: {
          position: 'absolute',
          top: '3px',
          right: '3px',
          width: '18px',
          height: '18px',
          borderRadius: '50%',
          border: 'none',
          background: 'rgba(0,0,0,0.55)',
          color: '#fff',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontSize: '11px',
          lineHeight: 1,
          zIndex: 10,
          padding: 0,
        }
      }, '×')
    ])
  ))
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
    
    // 如果有图片附件，拼接到 content 前面（Markdown 图片格式）
    if (msg.attachments && msg.attachments.length > 0) {
      const imageMarkdown = msg.attachments
        .map(att => `![image](${att.base64})`)
        .join('\n')
      content = imageMarkdown + (content ? '\n\n' + content : '')
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
      const html = marked.parse(content.replace(/\r\n/g, '\n'), { breaks: false, gfm: true })
      return h('div', { 
        innerHTML: html,
        class: 'markdown-body'
      })
    },
  },
  user: {
    placement: 'end',
    messageRender: (content) => {
      if (!content) return null
      
      // 分离图片和文本
      const imageRegex = /!\[image\]\((data:image[^)]+)\)/g
      const images = []
      let match
      while ((match = imageRegex.exec(content)) !== null) {
        images.push(match[1])
      }
      const textContent = content.replace(imageRegex, '').trim()
      
      const children = []
      
      // 渲染图片（小尺寸缩略图）
      if (images.length > 0) {
        const imgContainer = h('div', {
          style: {
            display: 'flex',
            flexWrap: 'wrap',
            gap: '6px',
            marginBottom: textContent ? '8px' : 0,
          }
        }, images.map((src, i) =>
          h(Image, {
            key: i,
            src,
            width: 120,
            height: 120,
            style: { 
              borderRadius: '8px', 
              objectFit: 'cover',
              cursor: 'pointer'
            },
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
