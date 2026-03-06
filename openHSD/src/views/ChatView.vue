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
          <Badge status="success" text="正常" />
          <span style="color: #999; font-size: 12px">版本 2026.2.27</span>
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
          <template v-if="chatMessages.length > 0">
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

// ==================== State ====================
const loading = ref(false)
const inputValue = ref('')
const attachmentsOpen = ref(false)
const attachedFiles = ref([])
const siderCollapsed = ref(false)

// 消息历史记录
const messageHistory = ref({})

// 会话列表
const conversationItems = ref([
  {
    key: 'default-0',
    label: '新对话',
    group: '今天',
  },
])

// 当前会话
const curConversation = ref('default-0')

// 当前会话的消息列表
const chatMessages = ref([])

// 当前选择的session
const currentSession = ref('Main Session')

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
  if (!val) return

  if (loading.value) {
    message.error('请求正在进行中，请稍候...')
    return
  }

  loading.value = true

  // 添加用户消息
  chatMessages.value = [
    ...chatMessages.value,
    { role: 'user', content: val },
  ]

  // 更新会话标签(用第一条消息)
  const currentConv = conversationItems.value.find(
    (c) => c.key === curConversation.value
  )
  if (
    currentConv &&
    (currentConv.label === '新对话' ||
      currentConv.label.startsWith('新对话 '))
  ) {
    currentConv.label = val.length > 20 ? val.substring(0, 20) + '...' : val
  }

  // 模拟 AI 响应
  setTimeout(() => {
    chatMessages.value = [
      ...chatMessages.value,
      {
        role: 'assistant',
        content: `这是对 "${val}" 的模拟回复。在实际项目中，这里将连接到真实的 AI 服务。\n\n您可以通过配置 API 接口来接入如 OpenAI、DeepSeek 等大语言模型服务。`,
      },
    ]
    loading.value = false

    // 保存到历史
    messageHistory.value[curConversation.value] = [...chatMessages.value]
  }, 1500)

  inputValue.value = ''
}

const onCreateConversation = () => {
  if (loading.value) {
    message.error('请等待当前请求完成后再创建新对话...')
    return
  }

  // 保存当前消息
  if (chatMessages.value.length > 0) {
    messageHistory.value[curConversation.value] = [...chatMessages.value]
  }

  const now = Date.now().toString()
  conversationItems.value = [
    {
      key: now,
      label: `新对话 ${conversationItems.value.length + 1}`,
      group: '今天',
    },
    ...conversationItems.value,
  ]
  curConversation.value = now
  chatMessages.value = []
}

const onConversationChange = (key) => {
  // 保存当前消息
  if (chatMessages.value.length > 0) {
    messageHistory.value[curConversation.value] = [...chatMessages.value]
  }

  curConversation.value = key
  chatMessages.value = messageHistory.value[key] || []
}

const onDeleteConversation = (key) => {
  conversationItems.value = conversationItems.value.filter(
    (item) => item.key !== key
  )

  if (key === curConversation.value) {
    const newKey = conversationItems.value[0]?.key || ''
    curConversation.value = newKey
    chatMessages.value = messageHistory.value[newKey] || []
  }

  delete messageHistory.value[key]
}

const onPromptClick = (info) => {
  onSubmit(info.data.description)
}

// ==================== Bubble items ====================
const bubbleItems = computed(() => {
  const items = chatMessages.value.map((msg, index) => ({
    key: index.toString(),
    role: msg.role,
    content: msg.content,
  }))

  // 如果正在加载，添加一个loading消息
  if (loading.value) {
    items.push({
      key: 'loading',
      role: 'assistant',
      content: '',
      loading: true,
    })
  }

  return items
})

const bubbleRoles = computed(() => ({
  assistant: {
    placement: 'start',
    typing: { step: 5, interval: 20 },
  },
  user: {
    placement: 'end',
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
</style>
