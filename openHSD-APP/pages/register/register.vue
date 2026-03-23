<template>
  <view class="page">
    <view class="map-bg"></view>
    <view class="inner">
      <view class="brand">
        <view class="brand-ring">
          <image
            class="brand-img"
            src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
            mode="aspectFit"
          />
        </view>
        <text class="brand-name">OPENHSD</text>
        <text class="brand-sub">网关对话</text>
      </view>

      <view v-if="errorMsg" class="strip strip--err">
        <text class="strip-msg">{{ errorMsg }}</text>
        <text class="strip-x" @tap.stop="errorMsg = ''">×</text>
      </view>

      <view v-if="successMsg" class="strip strip--ok">
        <text class="strip-msg strip-msg--ok">{{ successMsg }}</text>
      </view>

      <view class="field">
        <text class="label">用户名</text>
        <view
          class="line line--fill"
          :class="{ on: focusedField === 'username', bad: errors.username }"
        >
          <view class="field-icon-wrap">
            <CircleUser :size="18" :stroke-width="1.75" :color="iconColor" />
          </view>
          <input
            class="input"
            v-model="username"
            type="text"
            placeholder="请输入用户名"
            placeholder-class="ph"
            @focus="focusedField = 'username'"
            @blur="focusedField = ''"
          />
        </view>
        <text v-if="errors.username" class="hint">{{ errors.username }}</text>
      </view>

      <view class="field">
        <text class="label">密码</text>
        <view
          class="line line--fill"
          :class="{ on: focusedField === 'password', bad: errors.password }"
        >
          <view class="field-icon-wrap">
            <LockKeyhole :size="18" :stroke-width="1.75" :color="iconColor" />
          </view>
          <input
            class="input"
            v-model="password"
            :type="showPassword ? 'text' : 'password'"
            placeholder="至少 6 位"
            placeholder-class="ph"
            @focus="focusedField = 'password'"
            @blur="focusedField = ''"
          />
          <view class="eye-hit" @tap.stop="showPassword = !showPassword">
            <ScanEye v-if="showPassword" :size="18" :stroke-width="1.75" :color="iconColor" />
            <EyeClosed v-else :size="18" :stroke-width="1.75" :color="iconColor" />
          </view>
        </view>
        <text v-if="errors.password" class="hint">{{ errors.password }}</text>
      </view>

      <view class="field">
        <text class="label">确认密码</text>
        <view
          class="line line--fill"
          :class="{ on: focusedField === 'confirm', bad: errors.confirm }"
        >
          <view class="field-icon-wrap">
            <LockKeyhole :size="18" :stroke-width="1.75" :color="iconColor" />
          </view>
          <input
            class="input"
            v-model="confirm"
            :type="showPassword ? 'text' : 'password'"
            placeholder="再次输入密码"
            placeholder-class="ph"
            @focus="focusedField = 'confirm'"
            @blur="focusedField = ''"
          />
          <view class="eye-hit" @tap.stop="showPassword = !showPassword">
            <ScanEye v-if="showPassword" :size="18" :stroke-width="1.75" :color="iconColor" />
            <EyeClosed v-else :size="18" :stroke-width="1.75" :color="iconColor" />
          </view>
        </view>
        <text v-if="errors.confirm" class="hint">{{ errors.confirm }}</text>
      </view>

      <view class="btn" :class="{ loading }" @tap="onRegister">
        <view class="btn-in">
          <view v-if="loading" class="spin"></view>
          <text class="btn-t">{{ loading ? '注册中…' : '注册' }}</text>
        </view>
      </view>

      <view class="foot">
        <text class="foot-a">已有账号？</text>
        <text class="foot-b" @tap="goLogin">登录</text>
      </view>

      <text class="ver">openHSD · 2026</text>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { register as registerApi } from '../../api/auth.js'
import { CircleUser, LockKeyhole, ScanEye, EyeClosed } from 'lucide-vue-next'
import { ICON_COLOR } from '../../styles/theme-colors.js'

const iconColor = ICON_COLOR

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
    setTimeout(() => {
      uni.navigateTo({ url: '/pages/login/login', animationType: 'slide-in-right', animationDuration: 200 })
    }, 1200)
  } catch (e) {
    errorMsg.value = e.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const goLogin = () => {
  uni.navigateTo({ url: '/pages/login/login', animationType: 'slide-in-right', animationDuration: 200 })
}
</script>

<style lang="less" scoped>
@import '../../styles/theme.less';

.page {
  position: relative;
  min-height: 100vh;
  width: 100%;
  box-sizing: border-box;
  background: @ohsd-bg-page;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  padding: calc(28rpx + var(--status-bar-height, 0px)) 56rpx calc(36rpx + env(safe-area-inset-bottom, 0px));
  overflow: hidden;
}

.map-bg {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  pointer-events: none;
  opacity: @ohsd-map-opacity;
  background-image: url('@{ohsd-map-image}');
  background-repeat: no-repeat;
  background-position: @ohsd-map-position-x @ohsd-map-position-y;
  background-size: @ohsd-map-size;
}

.inner {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 620rpx;
  margin: 0 auto;
  padding: 0 8rpx;
  box-sizing: border-box;
}

.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: 56rpx;
}

.brand-ring {
  width: 200rpx;
  height: 200rpx;
  border-radius: 48rpx;
  background: linear-gradient(145deg, @ohsd-brand-ring-start 0%, @ohsd-brand-ring-end 100%);
  box-shadow: 0 12rpx 40rpx @ohsd-shadow-elevated;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 32rpx;
}

.brand-img {
  width: 112rpx;
  height: 112rpx;
  flex-shrink: 0;
}

.brand-name {
  font-size: 40rpx;
  font-weight: 700;
  color: @ohsd-text-primary;
  letter-spacing: 4rpx;
  line-height: 1.25;
  margin-bottom: 12rpx;
}

.brand-sub {
  font-size: 26rpx;
  color: @ohsd-text-muted;
  line-height: 1.35;
}

.title {
  display: block;
  width: 100%;
  text-align: center;
  font-size: 46rpx;
  font-weight: 600;
  color: @ohsd-text-primary;
  line-height: 1.25;
  margin-bottom: 18rpx;
}

.sub {
  display: block;
  width: 100%;
  text-align: center;
  font-size: 28rpx;
  color: @ohsd-text-tertiary;
  line-height: 1.55;
  margin-bottom: 48rpx;
}

.strip {
  display: flex;
  align-items: center;
  gap: 16rpx;
  width: 100%;
  box-sizing: border-box;
  padding: 24rpx 28rpx;
  margin-bottom: 36rpx;
  border-radius: 16rpx;
}

.strip--err {
  background: @ohsd-bg-error-strip;
}

.strip--ok {
  background: @ohsd-bg-success-strip;
}

.strip-msg {
  flex: 1;
  font-size: 28rpx;
  color: @ohsd-color-error-text;
  line-height: 1.45;
  text-align: left;
}

.strip-msg--ok {
  color: @ohsd-color-success-text;
}

.strip-x {
  flex-shrink: 0;
  font-size: 36rpx;
  color: @ohsd-color-error-dismiss;
  padding: 8rpx;
}

.field {
  margin-bottom: 44rpx;
}

.label {
  display: block;
  width: 100%;
  text-align: left;
  font-size: 28rpx;
  color: @ohsd-text-secondary;
  margin-bottom: 20rpx;
}

.line {
  border-bottom: 2rpx solid @ohsd-border-default;
  padding-bottom: 12rpx;
  transition: border-color 0.15s ease;
}

.line.on {
  border-bottom-color: @ohsd-border-focus;
}

.line.bad {
  border-bottom-color: @ohsd-border-error;
}

.line--fill {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.field-icon-wrap {
  width: 36rpx;
  height: 36rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.9;
}

.eye-hit {
  flex-shrink: 0;
  padding: 8rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.input {
  flex: 1;
  font-size: 32rpx;
  color: @ohsd-text-primary;
  height: 56rpx;
  line-height: 56rpx;
  min-height: 56rpx;
  text-align: left;
}

.ph {
  color: @ohsd-text-faint;
}

.hint {
  display: block;
  width: 100%;
  text-align: left;
  font-size: 24rpx;
  color: @ohsd-color-error;
  margin-top: 16rpx;
}

.btn {
  width: 100%;
  height: 100rpx;
  border-radius: 16rpx;
  margin-top: 20rpx;
  background: @ohsd-btn-primary-bg;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn.loading {
  opacity: 0.75;
}

.btn-in {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.btn-t {
  font-size: 32rpx;
  font-weight: 600;
  color: @ohsd-btn-primary-text;
}

.spin {
  width: 32rpx;
  height: 32rpx;
  border: 3rpx solid @ohsd-spin-track;
  border-top-color: @ohsd-btn-primary-text;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  margin-top: 48rpx;
}

.foot-a {
  font-size: 28rpx;
  color: @ohsd-text-subtle;
}

.foot-b {
  font-size: 28rpx;
  font-weight: 600;
  color: @ohsd-text-strong;
}

.ver {
  display: block;
  text-align: center;
  margin-top: 52rpx;
  font-size: 22rpx;
  color: @ohsd-text-caption;
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
