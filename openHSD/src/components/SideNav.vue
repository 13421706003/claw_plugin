<script setup>
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  MessageOutlined,
  SettingOutlined,
  PayCircleOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  CopyOutlined,
  SyncOutlined,
} from '@ant-design/icons-vue'
import { Button, Tooltip, theme } from 'ant-design-vue'
import { useUserStore } from '../stores/user.js'
import { request } from '../api/request.js'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { token: themeToken } = theme.useToken()

const collapsed = ref(false)
const showUserPanel = ref(false)
const tokenCopied = ref(false)
const clawList = ref([])
const loadingStatus = ref(false)

const navItems = [
  { key: '/chat', icon: MessageOutlined, label: '聊天' },
  { key: '/config', icon: SettingOutlined, label: '配置' },
  { key: '/recharge', icon: PayCircleOutlined, label: '充值' },
]

const activeKey = computed(() => route.path)

const navigateTo = (path) => {
  router.push(path)
}

const toggleCollapse = () => {
  collapsed.value = !collapsed.value
}

const copyToken = () => {
  navigator.clipboard.writeText(userStore.token.value).then(() => {
    tokenCopied.value = true
    setTimeout(() => { tokenCopied.value = false }, 2000)
  })
}

const onLogout = () => {
  userStore.logout()
  router.push('/login')
}

const fetchClawStatus = async () => {
  const userId = userStore.user.value?.userId
  if (!userId) return
  loadingStatus.value = true
  try {
    const res = await request(`/claw/status?userId=${userId}`)
    const data = await res.json()
    clawList.value = data.clawList || []
  } catch (e) {
    console.error('[SideNav] 查询机器状态失败：', e)
  } finally {
    loadingStatus.value = false
  }
}

const onToggleUserPanel = () => {
  showUserPanel.value = !showUserPanel.value
  if (showUserPanel.value) fetchClawStatus()
}

const formatHeartbeat = (ts) => {
  if (!ts) return '未知'
  const diff = Date.now() - ts
  if (diff < 60000) return `${Math.floor(diff / 1000)}s 前`
  return `${Math.floor(diff / 60000)}min 前`
}
</script>

<template>
  <div class="side-nav" :class="{ collapsed }">
    <div class="nav-header">
      <div class="logo">
        <img
          src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
          alt="logo"
          width="24"
          height="24"
          draggable="false"
        />
        <span v-show="!collapsed" class="logo-text">OPENHSD</span>
      </div>
      <Tooltip placement="right">
        <div class="collapse-btn" @click="toggleCollapse">
          <MenuUnfoldOutlined v-if="collapsed" />
          <MenuFoldOutlined v-else />
        </div>
      </Tooltip>
    </div>

    <div class="nav-menu">
      <Tooltip
        v-for="item in navItems"
        :key="item.key"
        :title="collapsed ? item.label : ''"
        placement="right"
      >
        <div
          class="nav-item"
          :class="{ active: activeKey === item.key }"
          @click="navigateTo(item.key)"
        >
          <component :is="item.icon" class="nav-icon" />
          <span v-show="!collapsed" class="nav-label">{{ item.label }}</span>
        </div>
      </Tooltip>
    </div>

    <div class="nav-footer">
      <div class="user-avatar" @click="onToggleUserPanel">
        <div class="avatar-circle">
          <UserOutlined />
        </div>
        <span v-show="!collapsed" class="username">
          {{ userStore.user?.value?.username || 'user' }}
        </span>
      </div>

      <div v-if="showUserPanel" class="user-panel">
        <div class="panel-header">
          <div class="panel-avatar">
            <UserOutlined />
          </div>
          <div class="panel-info">
            <div class="panel-username">{{ userStore.user.value?.username }}</div>
            <div class="panel-userid">userId: {{ userStore.user.value?.userId }}</div>
          </div>
        </div>

        <div class="panel-section">
          <div class="section-title">连接 Token</div>
          <div class="token-box">{{ userStore.token }}</div>
          <Button
            size="small"
            block
            :type="tokenCopied ? 'primary' : 'default'"
            @click="copyToken"
            style="margin-top: 8px; border-radius: 6px"
          >
            <template #icon><CopyOutlined /></template>
            {{ tokenCopied ? '已复制！' : '复制 Token' }}
          </Button>
          <div class="token-tip">
            将此 Token 填入插件 <code>cj.config.json</code> → <code>cloud.token</code>
          </div>
        </div>

        <div class="panel-section">
          <div class="section-header">
            <span class="section-title">在线机器</span>
            <span class="refresh-btn" @click="fetchClawStatus">
              {{ loadingStatus ? '刷新中...' : '刷新' }}
            </span>
          </div>

          <div v-if="clawList.length === 0" class="empty-claw">
            <div>暂无在线插件</div>
            <div>请启动 openHSD 插件并填入 Token</div>
          </div>

          <div
            v-for="claw in clawList"
            :key="claw.clawId"
            class="claw-item"
          >
            <div class="claw-header">
              <span class="claw-status-dot"></span>
              <span class="claw-id">{{ claw.clawId }}</span>
            </div>
            <div v-if="claw.openClawDeviceId" class="claw-device">
              OpenClaw: {{ claw.openClawDeviceId.substring(0, 24) }}...
            </div>
            <div class="claw-heartbeat">
              最后心跳：{{ formatHeartbeat(claw.lastHeartbeat) }}
            </div>
          </div>
        </div>

        <div class="panel-footer">
          <Button danger block size="small" @click="onLogout" style="border-radius: 6px">
            退出登录
          </Button>
        </div>
      </div>

      <div v-if="showUserPanel" class="panel-backdrop" @click="showUserPanel = false" />
    </div>
  </div>
</template>

<style scoped>
.side-nav {
  width: 200px;
  height: 100%;
  background: #fff;
  border-right: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  transition: width 0.25s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  z-index: 100;
}

.side-nav.collapsed {
  width: 64px;
}

.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
  min-height: 56px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  overflow: hidden;
}

.logo-text {
  font-weight: 600;
  font-size: 14px;
  white-space: nowrap;
}

.collapse-btn {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: pointer;
  color: #666;
  transition: all 0.2s;
  flex-shrink: 0;
}

.collapse-btn:hover {
  background: #f5f5f5;
  color: #1677ff;
}

.collapsed .collapse-btn {
  margin-left: auto;
  margin-right: auto;
}

.nav-menu {
  flex: 1;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  color: #595959;
  transition: all 0.2s;
  white-space: nowrap;
  overflow: hidden;
}

.nav-item:hover {
  background: #f0f7ff;
  color: #1677ff;
}

.nav-item.active {
  background: #e6f4ff;
  color: #1677ff;
  font-weight: 500;
}

.collapsed .nav-item {
  justify-content: center;
  padding: 10px;
}

.nav-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.nav-label {
  font-size: 14px;
}

.nav-footer {
  padding: 12px;
  border-top: 1px solid #f0f0f0;
  position: relative;
}

.user-avatar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.2s;
}

.user-avatar:hover {
  background: #f5f5f5;
}

.collapsed .user-avatar {
  justify-content: center;
  padding: 8px;
}

.avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4d4f, #d9363e);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  flex-shrink: 0;
}

.username {
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100px;
}

.user-panel {
  position: absolute;
  left: calc(100% + 8px);
  bottom: 0;
  width: 320px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12), 0 0 0 1px rgba(0, 0, 0, 0.06);
  z-index: 1000;
  overflow: hidden;
}

.panel-header {
  padding: 16px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.panel-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, #ff4d4f, #d9363e);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.panel-info {
  flex: 1;
  min-width: 0;
}

.panel-username {
  font-size: 14px;
  font-weight: 600;
  color: #1a1a1a;
}

.panel-userid {
  font-size: 11px;
  color: #999;
}

.panel-section {
  padding: 14px 16px 0;
}

.section-title {
  font-size: 11px;
  font-weight: 600;
  color: #999;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.refresh-btn {
  font-size: 11px;
  color: #1677ff;
  cursor: pointer;
}

.token-box {
  background: #f6f6f6;
  border-radius: 8px;
  padding: 10px 12px;
  font-size: 11px;
  font-family: monospace;
  color: #333;
  word-break: break-all;
  line-height: 1.6;
  max-height: 72px;
  overflow-y: auto;
  border: 1px solid #eee;
}

.token-tip {
  margin-top: 8px;
  padding: 7px 10px;
  background: #fff7e6;
  border-radius: 6px;
  border: 1px solid #ffd591;
  font-size: 11px;
  color: #d46b08;
  line-height: 1.6;
}

.token-tip code {
  background: #ffe7ba;
  padding: 1px 4px;
  border-radius: 3px;
}

.empty-claw {
  padding: 12px;
  background: #fafafa;
  border-radius: 8px;
  border: 1px dashed #e0e0e0;
  text-align: center;
  font-size: 12px;
  color: #bbb;
}

.empty-claw div + div {
  color: #ccc;
  margin-top: 2px;
}

.claw-item {
  padding: 10px 12px;
  background: #f8fffe;
  border: 1px solid #d9f7be;
  border-radius: 8px;
  margin-bottom: 6px;
}

.claw-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.claw-status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #52c41a;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px rgba(82, 196, 26, 0.2);
}

.claw-id {
  font-size: 12px;
  font-weight: 600;
  color: #1a1a1a;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.claw-device {
  font-size: 10px;
  color: #888;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.claw-heartbeat {
  font-size: 10px;
  color: #aaa;
  margin-top: 2px;
}

.panel-footer {
  padding: 12px 16px 14px;
}

.panel-backdrop {
  position: fixed;
  inset: 0;
  z-index: 999;
}
</style>
