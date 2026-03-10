<template>
  <view class="login-page">

    <!-- 背景光晕 -->
    <view class="bg-glow bg-glow-1"></view>
    <view class="bg-glow bg-glow-2"></view>
    <view class="bg-grid"></view>

    <!-- 登录卡片 -->
    <view class="login-card">

      <!-- Logo -->
      <view class="card-logo">
        <view class="logo-icon">
          <image
            src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
            mode="aspectFit"
            style="width: 32rpx; height: 32rpx;"
          />
        </view>
        <view class="logo-text">
          <text class="logo-name">OPENHSD</text>
          <text class="logo-sub">GATEWAY DASHBOARD</text>
        </view>
      </view>

      <!-- 标题 -->
      <view class="card-header">
        <text class="card-title">欢迎回来</text>
        <text class="card-subtitle">请登录您的账号以继续使用</text>
      </view>

      <!-- 错误提示 -->
      <view v-if="errorMsg" class="error-bar">
        <text class="error-icon">!</text>
        <text class="error-text">{{ errorMsg }}</text>
        <view class="error-close" @tap="errorMsg = ''">
          <text class="error-close-text">×</text>
        </view>
      </view>

      <!-- 用户名字段 -->
      <view class="field-group">
        <text class="field-label">用户名</text>
        <view class="field-wrap" :class="{ focused: focusedField === 'username', error: errors.username }">
          <text class="field-icon-text">👤</text>
          <input
            class="field-input"
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            placeholder-class="field-placeholder"
            @focus="focusedField = 'username'"
            @blur="focusedField = ''"
          />
        </view>
        <text v-if="errors.username" class="field-error">{{ errors.username }}</text>
      </view>

      <!-- 密码字段 -->
      <view class="field-group">
        <text class="field-label">密码</text>
        <view class="field-wrap" :class="{ focused: focusedField === 'password', error: errors.password }">
          <text class="field-icon-text">🔒</text>
          <input
            class="field-input"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            placeholder="请输入密码"
            placeholder-class="field-placeholder"
            @focus="focusedField = 'password'"
            @blur="focusedField = ''"
          />
          <view class="eye-btn" @tap="showPassword = !showPassword">
            <text class="eye-icon">{{ showPassword ? '👁' : '🙈' }}</text>
          </view>
        </view>
        <text v-if="errors.password" class="field-error">{{ errors.password }}</text>
      </view>

      <!-- 记住我 -->
      <view class="remember-row" @tap="remember = !remember">
        <view class="custom-checkbox" :class="{ checked: remember }">
          <text v-if="remember" class="checkbox-tick">✓</text>
        </view>
        <text class="remember-text">保持登录状态</text>
      </view>

      <!-- 登录按钮 -->
      <view class="login-btn" :class="{ loading }" @tap="onLogin">
        <view v-if="!loading" class="btn-inner">
          <text class="btn-text">登 录</text>
        </view>
        <view v-else class="btn-inner btn-loading">
          <view class="btn-spinner"></view>
          <text class="btn-text">登录中</text>
        </view>
      </view>

      <!-- 底部注册链接 -->
      <view class="card-footer">
        <text class="footer-text">还没有账号？</text>
        <text class="register-link" @tap="goRegister">立即注册</text>
      </view>

    </view>

    <!-- 底部版本号 -->
    <text class="page-version">openHSD · v2026</text>

  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { login as loginApi } from '../../api/auth.js'
import { useUserStore } from '../../store/user.js'

const userStore = useUserStore()

const username     = ref('')
const password     = ref('')
const remember     = ref(false)
const showPassword = ref(false)
const loading      = ref(false)
const errorMsg     = ref('')
const focusedField = ref('')
const errors       = reactive({ username: '', password: '' })

const validate = () => {
  errors.username = username.value.trim() ? '' : '请输入用户名'
  errors.password = password.value       ? '' : '请输入密码'
  return !errors.username && !errors.password
}

const onLogin = async () => {
  if (loading.value) return
  if (!validate()) return
  loading.value  = true
  errorMsg.value = ''
  try {
    const data = await loginApi(username.value.trim(), password.value)
    userStore.setAuth(data, remember.value)
    uni.reLaunch({ url: '/pages/chat/chat' })
  } catch (e) {
    errorMsg.value = e.message || '用户名或密码错误'
  } finally {
    loading.value = false
  }
}

const goRegister = () => {
  uni.navigateTo({ url: '/pages/register/register' })
}
</script>

<style scoped>
/* ===== 页面基础 ===== */
.login-page {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  position: relative;
  overflow: hidden;
  padding: 32rpx;
  padding-top: calc(32rpx + var(--status-bar-height, 0px));
  box-sizing: border-box;
}

/* ===== 背景网格 ===== */
.bg-grid {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(0,0,0,0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,0.02) 1px, transparent 1px);
  background-size: 64rpx 64rpx;
}

/* ===== 背景光晕 ===== */
.bg-glow {
  position: fixed;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(80rpx);
}
.bg-glow-1 {
  width: 500rpx; height: 400rpx;
  background: radial-gradient(circle, rgba(200, 210, 255, 0.25) 0%, transparent 70%);
  top: -100rpx; left: 50%;
  transform: translateX(-50%);
}
.bg-glow-2 {
  width: 400rpx; height: 400rpx;
  background: radial-gradient(circle, rgba(255, 200, 200, 0.18) 0%, transparent 70%);
  bottom: -80rpx; right: -60rpx;
}

/* ===== 登录卡片 ===== */
.login-card {
  position: relative;
  z-index: 10;
  width: 100%;
  background: #ffffff;
  border-radius: 32rpx;
  padding: 48rpx 40rpx 40rpx;
  box-shadow:
    0 0 0 1rpx rgba(0,0,0,0.05),
    0 4rpx 16rpx rgba(0,0,0,0.05),
    0 16rpx 48rpx rgba(0,0,0,0.06);
}

/* ===== Logo ===== */
.card-logo {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 40rpx;
}
.logo-icon {
  width: 60rpx; height: 60rpx;
  border-radius: 14rpx;
  background: #f2f2f7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.logo-text { display: flex; flex-direction: column; gap: 2rpx; }
.logo-name {
  font-size: 24rpx; font-weight: 700;
  color: rgba(0,0,0,0.85); letter-spacing: 4rpx; line-height: 1;
}
.logo-sub {
  font-size: 18rpx; font-weight: 500;
  color: rgba(0,0,0,0.3); letter-spacing: 2rpx; line-height: 1;
}

/* ===== 标题 ===== */
.card-header { margin-bottom: 36rpx; }
.card-title {
  display: block;
  font-size: 44rpx; font-weight: 600;
  color: rgba(0,0,0,0.88);
  line-height: 1.2; margin-bottom: 8rpx;
}
.card-subtitle {
  display: block;
  font-size: 26rpx; color: rgba(0,0,0,0.4); line-height: 1;
}

/* ===== 错误提示 ===== */
.error-bar {
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: rgba(255,59,48,0.06);
  border: 1rpx solid rgba(255,59,48,0.2);
  border-radius: 16rpx;
  padding: 16rpx 20rpx;
  margin-bottom: 28rpx;
}
.error-icon {
  width: 32rpx; height: 32rpx; border-radius: 50%;
  background: rgba(255,59,48,0.15);
  display: flex; align-items: center; justify-content: center;
  font-size: 20rpx; font-weight: 700;
  color: rgba(200,40,30,0.9);
  flex-shrink: 0;
  text-align: center; line-height: 32rpx;
}
.error-text { flex: 1; font-size: 24rpx; color: rgba(200,40,30,0.9); }
.error-close {
  width: 32rpx; height: 32rpx;
  display: flex; align-items: center; justify-content: center;
}
.error-close-text { font-size: 28rpx; color: rgba(200,40,30,0.5); line-height: 1; }

/* ===== 表单字段 ===== */
.field-group { margin-bottom: 24rpx; }
.field-label {
  display: block;
  font-size: 24rpx; font-weight: 500;
  color: rgba(0,0,0,0.55);
  margin-bottom: 12rpx;
}
.field-wrap {
  display: flex; align-items: center; gap: 12rpx;
  background: rgba(0,0,0,0.04);
  border: 2rpx solid transparent;
  border-radius: 16rpx;
  padding: 0 20rpx;
  height: 80rpx;
}
.field-wrap.focused {
  background: #ffffff;
  border-color: rgba(0,0,0,0.15);
}
.field-wrap.error {
  border-color: rgba(255,59,48,0.3);
  background: rgba(255,59,48,0.03);
}
.field-icon-text { font-size: 28rpx; flex-shrink: 0; }
.field-input {
  flex: 1; font-size: 28rpx;
  color: rgba(0,0,0,0.85); background: transparent;
  height: 80rpx; line-height: 80rpx;
}
.field-placeholder { color: rgba(0,0,0,0.25); }
.eye-btn { padding: 8rpx; }
.eye-icon { font-size: 26rpx; }
.field-error {
  display: block;
  font-size: 22rpx; color: rgba(255,59,48,0.85);
  margin-top: 8rpx;
}

/* ===== 记住我 ===== */
.remember-row {
  display: flex; align-items: center; gap: 12rpx;
  margin-bottom: 32rpx;
}
.custom-checkbox {
  width: 32rpx; height: 32rpx;
  border-radius: 8rpx;
  border: 2rpx solid rgba(0,0,0,0.2);
  background: transparent;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.custom-checkbox.checked {
  background: rgba(0,0,0,0.82);
  border-color: transparent;
}
.checkbox-tick { font-size: 20rpx; color: #fff; line-height: 1; }
.remember-text { font-size: 24rpx; color: rgba(0,0,0,0.5); }

/* ===== 登录按钮 ===== */
.login-btn {
  width: 100%;
  height: 84rpx;
  border-radius: 18rpx;
  background: rgba(0,0,0,0.84);
  display: flex; align-items: center; justify-content: center;
}
.login-btn.loading { opacity: 0.65; }
.btn-inner { display: flex; align-items: center; justify-content: center; gap: 12rpx; }
.btn-text { font-size: 28rpx; font-weight: 600; color: #ffffff; letter-spacing: 4rpx; }
.btn-loading { display: flex; align-items: center; gap: 12rpx; }
.btn-spinner {
  width: 32rpx; height: 32rpx;
  border: 3rpx solid rgba(255,255,255,0.3);
  border-top-color: #ffffff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

/* ===== 底部 ===== */
.card-footer {
  display: flex; align-items: center; justify-content: center;
  gap: 4rpx;
  margin-top: 32rpx;
}
.footer-text { font-size: 24rpx; color: rgba(0,0,0,0.35); }
.register-link { font-size: 24rpx; font-weight: 500; color: rgba(0,0,0,0.6); }

/* ===== 页面版本号 ===== */
.page-version {
  position: fixed;
  bottom: 32rpx; left: 50%;
  transform: translateX(-50%);
  font-size: 20rpx; color: rgba(0,0,0,0.2);
  white-space: nowrap; pointer-events: none; z-index: 5;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
</style>
