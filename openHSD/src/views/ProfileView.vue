<script setup>
import { ref, reactive, computed } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import { UserOutlined, LockOutlined, EditOutlined } from '@ant-design/icons-vue'
import { updateUsername, updatePassword } from '../api/auth'
import { useUserStore } from '../stores/user.js'

const router = useRouter()
const userStore = useUserStore()

// 当前用户信息
const currentUser = computed(() => userStore.username?.value || '未登录')

// 模态框控制
const usernameModalVisible = ref(false)
const passwordModalVisible = ref(false)

// 修改用户名表单
const usernameForm = reactive({
  newUsername: ''
})
const usernameLoading = ref(false)

// 修改密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const passwordLoading = ref(false)

// 打开修改用户名模态框
const openUsernameModal = () => {
  usernameForm.newUsername = currentUser.value
  usernameModalVisible.value = true
}

// 打开修改密码模态框
const openPasswordModal = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordModalVisible.value = true
}

// 修改用户名
const handleUpdateUsername = async () => {
  if (!usernameForm.newUsername.trim()) {
    message.error('新用户名不能为空')
    return
  }

  if (usernameForm.newUsername === currentUser.value) {
    message.warning('新用户名与当前用户名相同')
    return
  }

  usernameLoading.value = true
  try {
    const token = userStore.token.value
    console.log('[ProfileView] Token:', token)
    console.log('[ProfileView] Token type:', typeof token)
    console.log('[ProfileView] Token length:', token?.length)
    
    if (!token) {
      message.error('未登录，请先登录')
      usernameLoading.value = false
      return
    }
    
    await updateUsername(token, usernameForm.newUsername)
    message.success('用户名修改成功')
    
    // 更新 store 中的用户名
    userStore.setUsername(usernameForm.newUsername)
    
    // 关闭模态框
    usernameModalVisible.value = false
  } catch (error) {
    message.error(error.message || '修改用户名失败')
  } finally {
    usernameLoading.value = false
  }
}

// 修改密码
const handleUpdatePassword = async () => {
  if (!passwordForm.oldPassword) {
    message.error('请输入旧密码')
    return
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
    message.error('新密码长度不能少于6位')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    message.error('两次输入的密码不一致')
    return
  }

  passwordLoading.value = true
  try {
    const token = userStore.token.value
    await updatePassword(token, passwordForm.oldPassword, passwordForm.newPassword)
    message.success('密码修改成功，请重新登录')
    
    // 关闭模态框
    passwordModalVisible.value = false
    
    // 退出登录，跳转到登录页
    setTimeout(() => {
      userStore.logout()
      router.push('/login')
    }, 1500)
  } catch (error) {
    message.error(error.message || '修改密码失败')
  } finally {
    passwordLoading.value = false
  }
}

// 设置项数据
const settingsItems = computed(() => [
  {
    key: 'username',
    label: '用户名',
    value: currentUser.value,
    action: '修改',
    icon: UserOutlined,
    onClick: openUsernameModal
  },
  {
    key: 'password',
    label: '登录密码',
    value: '已设置',
    action: '修改',
    icon: LockOutlined,
    onClick: openPasswordModal
  }
])
</script>

<template>
  <div class="profile-view">
    <!-- 顶部用户信息区 -->
    <div class="user-header">
      <div class="user-avatar">
        <UserOutlined style="font-size: 24px; color: #1890ff;" />
      </div>
      <div class="user-info">
        <span class="user-label">用户名：</span>
        <span class="user-name">{{ currentUser }}</span>
      </div>
    </div>

    <!-- 设置列表区 -->
    <div class="settings-section">
      <div class="section-title">账号设置</div>
      <div class="settings-list">
        <div 
          v-for="item in settingsItems" 
          :key="item.key"
          class="settings-item"
        >
          <div class="item-icon">
            <component :is="item.icon" style="font-size: 16px; color: #1890ff;" />
          </div>
          <div class="item-label">{{ item.label }}</div>
          <div class="item-value">{{ item.value }}</div>
          <div class="item-action">
            <a-button 
              type="link" 
              size="small"
              @click="item.onClick"
            >
              <EditOutlined />
              {{ item.action }}
            </a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 修改用户名模态框 -->
    <a-modal
      v-model:open="usernameModalVisible"
      title="修改用户名"
      :confirm-loading="usernameLoading"
      @ok="handleUpdateUsername"
      @cancel="usernameModalVisible = false"
      width="480px"
    >
      <a-form layout="vertical">
        <a-form-item label="新用户名">
          <a-input
            v-model:value="usernameForm.newUsername"
            placeholder="请输入新用户名"
            size="large"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 修改密码模态框 -->
    <a-modal
      v-model:open="passwordModalVisible"
      title="修改密码"
      :confirm-loading="passwordLoading"
      @ok="handleUpdatePassword"
      @cancel="passwordModalVisible = false"
      width="480px"
    >
      <a-form layout="vertical">
        <a-form-item label="旧密码">
          <a-input-password
            v-model:value="passwordForm.oldPassword"
            placeholder="请输入旧密码"
            size="large"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="新密码">
          <a-input-password
            v-model:value="passwordForm.newPassword"
            placeholder="请输入新密码（至少6位）"
            size="large"
            allow-clear
          />
        </a-form-item>
        <a-form-item label="确认新密码">
          <a-input-password
            v-model:value="passwordForm.confirmPassword"
            placeholder="请再次输入新密码"
            size="large"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<style scoped>
.profile-view {
  width: 100%;
  height: 100%;
  overflow: auto;
  background: #f5f5f5;
  padding: 20px;
}

/* 顶部用户信息区 */
.user-header {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: linear-gradient(135deg, #e6f7ff 0%, #bae7ff 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
}

.user-label {
  font-size: 14px;
  color: #666;
}

.user-name {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

/* 设置列表区 */
.settings-section {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #333;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.settings-list {
  display: flex;
  flex-direction: column;
}

.settings-item {
  display: flex;
  align-items: center;
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
}

.settings-item:last-child {
  border-bottom: none;
}

.settings-item:hover {
  background: linear-gradient(90deg, #f0f9ff 0%, #ffffff 100%);
  margin: 0 -20px;
  padding: 16px 20px;
  border-radius: 6px;
}

.item-icon {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #e6f7ff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 12px;
  flex-shrink: 0;
  transition: all 0.3s;
}

.settings-item:hover .item-icon {
  background: #bae7ff;
  transform: scale(1.05);
}

.item-label {
  font-size: 14px;
  color: #666;
  width: 80px;
  flex-shrink: 0;
  font-weight: 500;
}

.item-value {
  flex: 1;
  font-size: 14px;
  color: #333;
  font-weight: 400;
}

.item-action {
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.3s;
}

.settings-item:hover .item-action {
  opacity: 1;
}

.item-action :deep(.ant-btn-link) {
  padding: 4px 12px;
  height: auto;
  color: #1890ff;
  font-size: 14px;
  border-radius: 4px;
  transition: all 0.3s;
}

.item-action :deep(.ant-btn-link:hover) {
  color: #fff;
  background: #1890ff;
}

/* 模态框样式 */
:deep(.ant-modal-content) {
  border-radius: 12px;
  overflow: hidden;
}

:deep(.ant-modal-header) {
  border-bottom: 1px solid #f0f0f0;
  padding: 20px 24px;
  background: #fafafa;
}

:deep(.ant-modal-title) {
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

:deep(.ant-modal-body) {
  padding: 24px;
}

:deep(.ant-modal-footer) {
  border-top: 1px solid #f0f0f0;
  padding: 16px 24px;
  background: #fafafa;
}

:deep(.ant-form-item) {
  margin-bottom: 20px;
}

:deep(.ant-form-item:last-child) {
  margin-bottom: 0;
}

:deep(.ant-form-item-label > label) {
  color: #333;
  font-weight: 500;
  font-size: 14px;
}

:deep(.ant-input),
:deep(.ant-input-password) {
  border-radius: 8px;
  border-color: #d9d9d9;
  transition: all 0.3s;
}

:deep(.ant-input:hover),
:deep(.ant-input-password:hover) {
  border-color: #40a9ff;
}

:deep(.ant-input:focus),
:deep(.ant-input-password:focus) {
  border-color: #1890ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.1);
}

:deep(.ant-btn) {
  border-radius: 8px;
  height: 38px;
  font-weight: 500;
  transition: all 0.3s;
}

:deep(.ant-btn-primary) {
  background: #1890ff;
  border-color: #1890ff;
}

:deep(.ant-btn-primary:hover) {
  background: #40a9ff;
  border-color: #40a9ff;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(24, 144, 255, 0.3);
}

:deep(.ant-btn-default:hover) {
  color: #1890ff;
  border-color: #1890ff;
}

/* 响应式 */
@media (max-width: 768px) {
  .profile-view {
    padding: 12px;
  }

  .user-header {
    padding: 12px 16px;
  }

  .user-avatar {
    width: 40px;
    height: 40px;
  }

  .user-avatar :deep(.anticon) {
    font-size: 20px;
  }

  .settings-section {
    padding: 16px;
  }

  .settings-item {
    flex-wrap: wrap;
    gap: 8px;
  }

  .item-icon {
    width: 28px;
    height: 28px;
    margin-right: 8px;
  }

  .item-label {
    width: auto;
    flex: 1;
  }

  .item-value {
    width: 100%;
    order: 3;
    margin-left: 36px;
    font-size: 13px;
    color: #666;
  }

  .item-action {
    width: auto;
    opacity: 1;
  }

  .settings-item:hover {
    margin: 0 -16px;
    padding: 16px;
    background: #fafafa;
  }

  .settings-item:hover .item-icon {
    transform: none;
  }

  .item-action :deep(.ant-btn-link) {
    padding: 2px 8px;
    font-size: 13px;
  }

  .item-action :deep(.ant-btn-link:hover) {
    background: transparent;
    color: #1890ff;
  }
}
</style>
