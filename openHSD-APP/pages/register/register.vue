<template>
  <view class="login-page">

    <!-- 背景层 -->
    <view class="bg-glow bg-glow-1"></view>
    <view class="bg-glow bg-glow-2"></view>
    <view class="bg-grid"></view>

    <!-- 注册卡片 -->
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
        <text class="card-title">创建账号</text>
        <text class="card-subtitle">注册后即可开始使用</text>
      </view>

      <!-- 错误提示 -->
      <view v-if="errorMsg" class="error-bar">
        <text class="error-icon">!</text>
        <text class="error-text">{{ errorMsg }}</text>
        <view class="error-close" @tap="errorMsg = ''">
          <text class="error-close-text">×</text>
        </view>
      </view>

      <!-- 成功提示 -->
      <view v-if="successMsg" class="success-bar">
        <text class="success-icon">✓</text>
        <text class="success-text">{{ successMsg }}</text>
      </view>

      <!-- 用户名 -->
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

      <!-- 密码 -->
      <view class="field-group">
        <text class="field-label">密码</text>
        <view class="field-wrap" :class="{ focused: focusedField === 'password', error: errors.password }">
          <text class="field-icon-text">🔒</text>
          <input
            class="field-input"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            placeholder="至少 6 位字符"
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

      <!-- 确认密码 -->
      <view class="field-group">
        <text class="field-label">确认密码</text>
        <view class="field-wrap" :class="{ focused: focusedField === 'confirm', error: errors.confirm }">
          <text class="field-icon-text">🔒</text>
          <input
            class="field-input"
            v-model="confirm"
            :type="showPassword ? 'text' : 'password'"
            placeholder="再次输入密码"
            placeholder-class="field-placeholder"
            @focus="focusedField = 'confirm'"
            @blur="focusedField = ''"
          />
        </view>
        <text v-if="errors.confirm" class="field-error">{{ errors.confirm }}</text>
      </view>

      <!-- 注册按钮 -->
      <view class="login-btn" :class="{ loading }" @tap="onRegister">
        <view class="btn-inner">
          <view v-if="loading" class="btn-spinner"></view>
          <text class="btn-text">{{ loading ? '注册中' : '立即注册' }}</text>
        </view>
      </view>

      <!-- 底部登录链接 -->
      <view class="card-footer">
        <text class="footer-text">已有账号？</text>
        <text class="register-link" @tap="goLogin">立即登录</text>
      </view>

    </view>

    <text class="page-version">openHSD · v2026</text>

  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { register as registerApi } from '../../api/auth.js'

const username     = ref('')
const password     = ref('')
const confirm      = ref('')
const showPassword = ref(false)
const loading      = ref(false)
const errorMsg     = ref('')
const successMsg   = ref('')
const focusedField = ref('')
const errors       = reactive({ username: '', password: '', confirm: '' })

const validate = () => {
  errors.username = username.value.trim()       ? '' : '请输入用户名'
  errors.password = password.value.length >= 6  ? '' : '密码至少 6 位字符'
  errors.confirm  = confirm.value === password.value ? '' : '两次密码不一致'
  return !errors.username && !errors.password && !errors.confirm
}

const onRegister = async () => {
  if (loading.value) return
  if (!validate()) return
  loading.value  = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await registerApi(username.value.trim(), password.value)
    successMsg.value = '注册成功！正在跳转...'
    setTimeout(() => { uni.navigateBack() }, 1200)
  } catch (e) {
    errorMsg.value = e.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const goLogin = () => { uni.navigateBack() }
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
  padding-top: calc(24rpx + var(--status-bar-height, 0px));
  box-sizing: border-box;
}

.bg-grid {
  position: fixed;
  top: 0; left: 0; right: 0; bottom: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(0,0,0,0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,0.02) 1px, transparent 1px);
  background-size: 64rpx 64rpx;
}
.bg-glow { position: fixed; border-radius: 50%; pointer-events: none; filter: blur(80rpx); }
.bg-glow-1 {
  width: 500rpx; height: 400rpx;
  background: radial-gradient(circle, rgba(200,210,255,0.22) 0%, transparent 70%);
  top: -120rpx; left: 50%; transform: translateX(-50%);
}
.bg-glow-2 {
  width: 400rpx; height: 350rpx;
  background: radial-gradient(circle, rgba(255,200,200,0.15) 0%, transparent 70%);
  bottom: -80rpx; right: -60rpx;
}

/* ===== 卡片 ===== */
.login-card {
  position: relative; z-index: 10;
  width: 100%;
  background: #ffffff; border-radius: 28rpx;
  padding: 40rpx 36rpx 36rpx;
  box-shadow: 0 4rpx 24rpx rgba(0,0,0,0.06), 0 16rpx 48rpx rgba(0,0,0,0.05);
}
.card-logo { display: flex; align-items: center; gap: 16rpx; margin-bottom: 32rpx; }
.logo-icon { width: 56rpx; height: 56rpx; border-radius: 14rpx; background: #f2f2f7; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.logo-text { display: flex; flex-direction: column; gap: 2rpx; }
.logo-name { font-size: 22rpx; font-weight: 700; color: rgba(0,0,0,0.85); letter-spacing: 4rpx; line-height: 1; }
.logo-sub { font-size: 16rpx; font-weight: 500; color: rgba(0,0,0,0.3); letter-spacing: 1rpx; line-height: 1; }

.card-header { margin-bottom: 28rpx; }
.card-title { display: block; font-size: 40rpx; font-weight: 600; color: rgba(0,0,0,0.88); line-height: 1.2; margin-bottom: 6rpx; }
.card-subtitle { display: block; font-size: 24rpx; color: rgba(0,0,0,0.4); line-height: 1; }

.error-bar {
  display: flex; align-items: center; gap: 10rpx;
  background: rgba(255,59,48,0.06); border: 1rpx solid rgba(255,59,48,0.2);
  border-radius: 14rpx; padding: 14rpx 18rpx; margin-bottom: 24rpx;
}
.error-icon { font-size: 20rpx; font-weight: 700; color: rgba(200,40,30,0.9); flex-shrink: 0; }
.error-text { flex: 1; font-size: 24rpx; color: rgba(200,40,30,0.9); }
.error-close { width: 36rpx; height: 36rpx; display: flex; align-items: center; justify-content: center; }
.error-close-text { font-size: 28rpx; color: rgba(200,40,30,0.5); line-height: 1; }

.success-bar {
  display: flex; align-items: center; gap: 10rpx;
  background: rgba(82,196,26,0.06); border: 1rpx solid rgba(82,196,26,0.25);
  border-radius: 14rpx; padding: 14rpx 18rpx; margin-bottom: 24rpx;
}
.success-icon { font-size: 22rpx; font-weight: 700; color: rgba(52,150,10,0.9); flex-shrink: 0; }
.success-text { flex: 1; font-size: 24rpx; color: rgba(52,150,10,0.9); }

.field-group { margin-bottom: 20rpx; }
.field-label { display: block; font-size: 24rpx; font-weight: 500; color: rgba(0,0,0,0.55); margin-bottom: 10rpx; }
.field-wrap {
  display: flex; align-items: center; gap: 12rpx;
  background: rgba(0,0,0,0.04); border: 2rpx solid transparent;
  border-radius: 14rpx; padding: 0 18rpx; height: 76rpx;
}
.field-wrap.focused { background: #ffffff; border-color: rgba(0,0,0,0.15); }
.field-wrap.error { border-color: rgba(255,59,48,0.3); background: rgba(255,59,48,0.03); }
.field-icon-text { font-size: 26rpx; flex-shrink: 0; }
.field-input { flex: 1; font-size: 26rpx; color: rgba(0,0,0,0.85); background: transparent; height: 76rpx; line-height: 76rpx; }
.field-placeholder { color: rgba(0,0,0,0.25); }
.eye-btn { padding: 6rpx; }
.eye-icon { font-size: 24rpx; }
.field-error { display: block; font-size: 20rpx; color: rgba(255,59,48,0.85); margin-top: 6rpx; }

.login-btn {
  width: 100%; height: 80rpx; border-radius: 16rpx;
  background: rgba(0,0,0,0.84); display: flex; align-items: center; justify-content: center;
  margin-top: 8rpx;
}
.login-btn.loading { opacity: 0.65; }
.btn-inner { display: flex; align-items: center; justify-content: center; gap: 12rpx; }
.btn-text { font-size: 26rpx; font-weight: 600; color: #ffffff; letter-spacing: 3rpx; }
.btn-spinner { width: 30rpx; height: 30rpx; border: 3rpx solid rgba(255,255,255,0.3); border-top-color: #fff; border-radius: 50%; animation: spin 0.8s linear infinite; }

.card-footer { display: flex; align-items: center; justify-content: center; gap: 4rpx; margin-top: 28rpx; }
.footer-text { font-size: 24rpx; color: rgba(0,0,0,0.35); }
.register-link { font-size: 24rpx; font-weight: 500; color: rgba(0,0,0,0.6); }

.page-version { position: fixed; bottom: 28rpx; left: 50%; transform: translateX(-50%); font-size: 20rpx; color: rgba(0,0,0,0.2); white-space: nowrap; pointer-events: none; z-index: 5; }

@keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
</style>
