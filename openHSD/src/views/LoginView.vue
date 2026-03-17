<template>
  <div class="login-page">

    <!-- 背景光晕层 -->
    <div class="bg-glow bg-glow-1"></div>
    <div class="bg-glow bg-glow-2"></div>
    <div class="bg-glow bg-glow-3"></div>

    <!-- 背景网格 -->
    <div class="bg-grid"></div>

    <!-- 背景浮动装饰圆圈 -->
    <div class="bg-orb bg-orb-1"></div>
    <div class="bg-orb bg-orb-2"></div>
    <div class="bg-orb bg-orb-3"></div>
    <div class="bg-orb bg-orb-4"></div>

    <!-- 左上角装饰卡片 -->
    <div class="deco-card deco-card-tl">
      <div class="deco-card-bar"></div>
      <div class="deco-card-line"></div>
      <div class="deco-card-line short"></div>
    </div>

    <!-- 右下角装饰卡片 -->
    <div class="deco-card deco-card-br">
      <div class="deco-card-circle"></div>
      <div class="deco-card-line"></div>
      <div class="deco-card-line short"></div>
    </div>

    <!-- 右上角浮动标签 -->
    <div class="deco-tag deco-tag-tr">
      <span class="deco-tag-dot"></span>
      <span class="deco-tag-text">AI Gateway · Online</span>
    </div>

    <!-- 左下角浮动标签 -->
    <div class="deco-tag deco-tag-bl">
      <span class="deco-tag-dot pulse"></span>
      <span class="deco-tag-text">openHSD v2026</span>
    </div>

    <!-- 登录卡片 -->
    <div class="login-card" ref="cardRef">

      <!-- Logo 区 -->
      <div class="card-logo">
        <div class="logo-icon">
          <img
            src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
            alt="logo"
            width="28"
            height="28"
            draggable="false"
          />
        </div>
        <div class="logo-text">
          <span class="logo-name">OPENHSD</span>
          <span class="logo-sub">GATEWAY DASHBOARD</span>
        </div>
      </div>

      <!-- 标题 -->
      <div class="card-header">
        <h1 class="card-title">欢迎回来</h1>
        <p class="card-subtitle">请登录您的账号以继续使用</p>
      </div>

      <!-- 错误提示 -->
      <transition name="fade-slide">
        <div v-if="errorMsg" class="error-bar">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="6.5" stroke="rgba(255,59,48,0.8)" />
            <path d="M7 4v3.5M7 9.5v.5" stroke="rgba(255,59,48,0.9)" stroke-width="1.3" stroke-linecap="round"/>
          </svg>
          <span>{{ errorMsg }}</span>
          <button class="error-close" @click="errorMsg = ''">
            <svg width="10" height="10" viewBox="0 0 10 10" fill="none">
              <path d="M1 1l8 8M9 1L1 9" stroke="rgba(255,59,48,0.6)" stroke-width="1.3" stroke-linecap="round"/>
            </svg>
          </button>
        </div>
      </transition>

      <!-- 表单 -->
      <form class="login-form" @submit.prevent="onLogin">

        <!-- 用户名 -->
        <div class="field-group">
          <label class="field-label">用户名</label>
          <div class="field-wrap" :class="{ focused: focusedField === 'username', filled: formState.username }">
            <svg class="field-icon" width="15" height="15" viewBox="0 0 15 15" fill="none">
              <circle cx="7.5" cy="5" r="2.8" stroke="currentColor" stroke-width="1.2"/>
              <path d="M1.5 13c0-2.8 2.7-5 6-5s6 2.2 6 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
            </svg>
            <input
              v-model="formState.username"
              type="text"
              class="field-input"
              placeholder="请输入用户名"
              autocomplete="username"
              @focus="focusedField = 'username'"
              @blur="focusedField = ''"
            />
          </div>
          <transition name="fade-slide">
            <span v-if="errors.username" class="field-error">{{ errors.username }}</span>
          </transition>
        </div>

        <!-- 密码 -->
        <div class="field-group">
          <label class="field-label">
            密码
            <a class="forgot-link" href="#">忘记密码？</a>
          </label>
          <div class="field-wrap" :class="{ focused: focusedField === 'password', filled: formState.password }">
            <svg class="field-icon" width="15" height="15" viewBox="0 0 15 15" fill="none">
              <rect x="2.5" y="6.5" width="10" height="7" rx="1.5" stroke="currentColor" stroke-width="1.2"/>
              <path d="M5 6.5V4.5a2.5 2.5 0 015 0v2" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
            </svg>
            <input
              v-model="formState.password"
              :type="showPassword ? 'text' : 'password'"
              class="field-input"
              placeholder="请输入密码"
              autocomplete="current-password"
              @focus="focusedField = 'password'"
              @blur="focusedField = ''"
            />
            <button type="button" class="eye-btn" @click="showPassword = !showPassword" tabindex="-1">
              <svg v-if="!showPassword" width="15" height="15" viewBox="0 0 15 15" fill="none">
                <path d="M1 7.5C1 7.5 3.5 3 7.5 3s6.5 4.5 6.5 4.5-2.5 4.5-6.5 4.5S1 7.5 1 7.5z" stroke="currentColor" stroke-width="1.2"/>
                <circle cx="7.5" cy="7.5" r="1.8" stroke="currentColor" stroke-width="1.2"/>
              </svg>
              <svg v-else width="15" height="15" viewBox="0 0 15 15" fill="none">
                <path d="M2 2l11 11M6.3 5.1A4.5 4.5 0 0113 7.5s-1 1.8-2.8 3M5 4.5C3.2 5.6 2 7.5 2 7.5s2.5 4.5 5.5 4.5c1.1 0 2-.3 2.8-.8" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
              </svg>
            </button>
          </div>
          <transition name="fade-slide">
            <span v-if="errors.password" class="field-error">{{ errors.password }}</span>
          </transition>
        </div>

        <!-- 记住我 -->
        <label class="remember-row">
          <div class="custom-checkbox" :class="{ checked: formState.remember }" @click="formState.remember = !formState.remember">
            <svg v-if="formState.remember" width="10" height="10" viewBox="0 0 10 10" fill="none">
              <path d="M1.5 5l2.5 2.5 5-5" stroke="white" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
          <span class="remember-text">保持登录状态</span>
        </label>

        <!-- 登录按钮 -->
        <button
          type="submit"
          class="login-btn"
          :class="{ loading }"
          :disabled="loading"
        >
          <span v-if="!loading" class="btn-text">登 录</span>
          <span v-else class="btn-loading">
            <svg class="spin" width="16" height="16" viewBox="0 0 16 16" fill="none">
              <circle cx="8" cy="8" r="6" stroke="rgba(255,255,255,0.3)" stroke-width="1.5"/>
              <path d="M8 2a6 6 0 016 6" stroke="white" stroke-width="1.5" stroke-linecap="round"/>
            </svg>
            登录中
          </span>
        </button>

      </form>

      <!-- 底部 -->
      <p class="card-footer">
        还没有账号？<router-link class="register-link" to="/register">立即注册</router-link>
      </p>

    </div>

    <!-- 底部版本号 -->
    <p class="page-version">openHSD · 版本 2026.3.6</p>

  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user.js'
import gsap from 'gsap'

const router    = useRouter()
const userStore = useUserStore()

const loading      = ref(false)
const errorMsg     = ref('')
const focusedField = ref('')
const showPassword = ref(false)
const cardRef      = ref(null)

const formState = reactive({ username: '', password: '', remember: false })
const errors    = reactive({ username: '', password: '' })

const validate = () => {
  errors.username = formState.username.trim() ? '' : '请输入用户名'
  errors.password = formState.password       ? '' : '请输入密码'
  return !errors.username && !errors.password
}

const onLogin = async () => {
  if (!validate()) return
  loading.value  = true
  errorMsg.value = ''
  try {
    await userStore.login(formState.username, formState.password, formState.remember)
    router.push('/chat')
  } catch (e) {
    errorMsg.value = e.message || '用户名或密码错误'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const state = history.state
  if (state?.username) {
    formState.username = state.username
  }
  if (state?.password) {
    formState.password = state.password
  }

  if (cardRef.value) {
    gsap.fromTo(cardRef.value, 
      { opacity: 0, y: 30, scale: 0.95 },
      { opacity: 1, y: 0, scale: 1, duration: 0.5, ease: 'power2.out' }
    )
  }
})

onBeforeUnmount(() => {
  if (cardRef.value) {
    gsap.to(cardRef.value, {
      opacity: 0,
      y: -20,
      scale: 0.98,
      duration: 0.3,
      ease: 'power2.in'
    })
  }
})
</script>

<style scoped>
/* ============================================================
   基础 & 页面
   ============================================================ */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  font-family: -apple-system, 'SF Pro Display', 'SF Pro Text',
               BlinkMacSystemFont, 'Helvetica Neue', sans-serif;
  position: relative;
  overflow: hidden;
}

/* ============================================================
   背景网格
   ============================================================ */
.bg-grid {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(0,0,0,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,0.03) 1px, transparent 1px);
  background-size: 48px 48px;
  mask-image: radial-gradient(ellipse 80% 80% at 50% 50%, black 0%, transparent 100%);
  -webkit-mask-image: radial-gradient(ellipse 80% 80% at 50% 50%, black 0%, transparent 100%);
}

/* ============================================================
   背景氛围光
   ============================================================ */
.bg-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(100px);
}
.bg-glow-1 {
  width: 700px; height: 500px;
  background: radial-gradient(circle, rgba(200, 210, 255, 0.35) 0%, transparent 70%);
  top: -150px; left: 50%;
  transform: translateX(-60%);
}
.bg-glow-2 {
  width: 500px; height: 500px;
  background: radial-gradient(circle, rgba(255, 200, 200, 0.25) 0%, transparent 70%);
  bottom: -100px; left: -80px;
}
.bg-glow-3 {
  width: 450px; height: 450px;
  background: radial-gradient(circle, rgba(200, 240, 220, 0.2) 0%, transparent 70%);
  top: -80px; right: -60px;
}

/* ============================================================
   背景浮动圆圈
   ============================================================ */
.bg-orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
  border: 1px solid rgba(0, 0, 0, 0.05);
}
.bg-orb-1 {
  width: 320px; height: 320px;
  top: 8%; left: 6%;
  background: radial-gradient(circle at 35% 35%, rgba(255,255,255,0.9), rgba(240,242,255,0.3));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.8), 0 4px 24px rgba(0,0,0,0.04);
  animation: float 8s ease-in-out infinite;
}
.bg-orb-2 {
  width: 180px; height: 180px;
  top: 15%; right: 10%;
  background: radial-gradient(circle at 40% 30%, rgba(255,255,255,0.95), rgba(255,245,245,0.4));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.9), 0 3px 16px rgba(0,0,0,0.04);
  animation: float 6s ease-in-out infinite reverse;
}
.bg-orb-3 {
  width: 240px; height: 240px;
  bottom: 10%; right: 8%;
  background: radial-gradient(circle at 30% 30%, rgba(255,255,255,0.9), rgba(240,255,248,0.3));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.8), 0 4px 20px rgba(0,0,0,0.03);
  animation: float 10s ease-in-out infinite;
}
.bg-orb-4 {
  width: 120px; height: 120px;
  bottom: 20%; left: 12%;
  background: radial-gradient(circle at 40% 35%, rgba(255,255,255,0.95), rgba(245,240,255,0.4));
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.9), 0 2px 12px rgba(0,0,0,0.04);
  animation: float 7s ease-in-out infinite reverse;
}

@keyframes float {
  0%, 100% { transform: translateY(0px); }
  50%       { transform: translateY(-12px); }
}

/* ============================================================
   背景装饰卡片
   ============================================================ */
.deco-card {
  position: absolute;
  pointer-events: none;
  background: rgba(255,255,255,0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 0.5px solid rgba(0,0,0,0.07);
  border-radius: 14px;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05), 0 8px 32px rgba(0,0,0,0.04);
  width: 160px;
}
.deco-card-tl {
  top: 8%;
  left: 5%;
  animation: float 9s ease-in-out infinite;
}
.deco-card-br {
  bottom: 10%;
  right: 5%;
  animation: float 11s ease-in-out infinite reverse;
  width: 140px;
}
.deco-card-bar {
  height: 8px;
  width: 60%;
  border-radius: 4px;
  background: linear-gradient(90deg, rgba(0,0,0,0.1), rgba(0,0,0,0.04));
}
.deco-card-line {
  height: 6px;
  width: 100%;
  border-radius: 3px;
  background: rgba(0,0,0,0.05);
}
.deco-card-line.short { width: 65%; }
.deco-card-circle {
  width: 28px; height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(0,0,0,0.07), rgba(0,0,0,0.03));
  margin-bottom: 2px;
}

/* ============================================================
   背景浮动标签
   ============================================================ */
.deco-tag {
  position: absolute;
  pointer-events: none;
  display: flex;
  align-items: center;
  gap: 6px;
  background: rgba(255,255,255,0.8);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 0.5px solid rgba(0,0,0,0.07);
  border-radius: 100px;
  padding: 7px 14px 7px 10px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.deco-tag-tr {
  top: 7%;
  right: 7%;
  animation: float 7s ease-in-out infinite;
}
.deco-tag-bl {
  bottom: 8%;
  left: 6%;
  animation: float 8s ease-in-out infinite reverse;
}
.deco-tag-dot {
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #34c759;
  flex-shrink: 0;
  box-shadow: 0 0 0 2px rgba(52, 199, 89, 0.2);
}
.deco-tag-dot.pulse {
  animation: pulse-dot 2s ease-in-out infinite;
}
@keyframes pulse-dot {
  0%, 100% { box-shadow: 0 0 0 2px rgba(52, 199, 89, 0.2); }
  50%       { box-shadow: 0 0 0 5px rgba(52, 199, 89, 0.1); }
}
.deco-tag-text {
  font-size: 11px;
  font-weight: 500;
  color: rgba(0,0,0,0.45);
  letter-spacing: 0.2px;
  white-space: nowrap;
}

/* ============================================================
   登录卡片
   ============================================================ */
.login-card {
  position: relative;
  z-index: 10;
  width: 400px;
  background: #ffffff;
  border-radius: 20px;
  padding: 40px 40px 32px;

  /* 多层环境阴影 — 模拟真实物理光效 */
  box-shadow:
    0 0 0 0.5px rgba(0, 0, 0, 0.06),
    0 1px 2px rgba(0, 0, 0, 0.04),
    0 4px 12px rgba(0, 0, 0, 0.05),
    0 12px 32px rgba(0, 0, 0, 0.07),
    0 32px 80px rgba(0, 0, 0, 0.07);
}

/* ============================================================
   Logo 区
   ============================================================ */
.card-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 32px;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 9px;
  background: #f2f2f7;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.logo-name {
  font-size: 13px;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  letter-spacing: 1.5px;
  line-height: 1;
}

.logo-sub {
  font-size: 9.5px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.3);
  letter-spacing: 1px;
  line-height: 1;
}

/* ============================================================
   标题区
   ============================================================ */
.card-header {
  margin-bottom: 28px;
}

.card-title {
  font-size: 26px;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.88);
  letter-spacing: -0.5px;
  line-height: 1.2;
  margin-bottom: 6px;
}

.card-subtitle {
  font-size: 13.5px;
  color: rgba(0, 0, 0, 0.4);
  font-weight: 400;
  line-height: 1;
}

/* ============================================================
   错误提示
   ============================================================ */
.error-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(255, 59, 48, 0.06);
  border: 0.5px solid rgba(255, 59, 48, 0.2);
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 20px;
  font-size: 13px;
  color: rgba(200, 40, 30, 0.9);
}

.error-bar span { flex: 1; }

.error-close {
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px;
  display: flex;
  align-items: center;
  opacity: 0.6;
  transition: opacity 0.15s;
}
.error-close:hover { opacity: 1; }

/* ============================================================
   表单
   ============================================================ */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 0;
}

/* 字段组 */
.field-group {
  margin-bottom: 16px;
}

.field-label {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12.5px;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.55);
  letter-spacing: 0.1px;
  margin-bottom: 7px;
  user-select: none;
}

.forgot-link {
  font-size: 12.5px;
  color: rgba(0, 0, 0, 0.4);
  text-decoration: none;
  font-weight: 400;
  transition: color 0.15s;
}
.forgot-link:hover { color: rgba(0, 0, 0, 0.75); }

/* 输入框容器 */
.field-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid transparent;
  border-radius: 10px;
  padding: 0 13px;
  height: 44px;
  transition: all 0.18s ease;
}

.field-wrap.focused {
  background: #ffffff;
  border-color: rgba(0, 0, 0, 0.18);
  box-shadow: 0 0 0 3px rgba(0, 0, 0, 0.05);
}

.field-icon {
  color: rgba(0, 0, 0, 0.28);
  flex-shrink: 0;
  transition: color 0.18s;
}
.field-wrap.focused .field-icon { color: rgba(0, 0, 0, 0.5); }

.field-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: rgba(0, 0, 0, 0.85);
  font-family: inherit;
  font-weight: 400;
  letter-spacing: 0.1px;
  min-width: 0;
}
.field-input::placeholder { color: rgba(0, 0, 0, 0.25); }

/* 密码可见按钮 */
.eye-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px;
  color: rgba(0, 0, 0, 0.28);
  display: flex;
  align-items: center;
  transition: color 0.15s;
  flex-shrink: 0;
}
.eye-btn:hover { color: rgba(0, 0, 0, 0.55); }

/* 字段错误 */
.field-error {
  display: block;
  font-size: 11.5px;
  color: rgba(255, 59, 48, 0.85);
  margin-top: 5px;
  margin-left: 2px;
}

/* ============================================================
   记住我
   ============================================================ */
.remember-row {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  margin-bottom: 20px;
  user-select: none;
}

.custom-checkbox {
  width: 16px;
  height: 16px;
  border-radius: 4.5px;
  border: 1.5px solid rgba(0, 0, 0, 0.2);
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
  flex-shrink: 0;
}

.custom-checkbox.checked {
  background: rgba(0, 0, 0, 0.82);
  border-color: transparent;
}

.remember-text {
  font-size: 13px;
  color: rgba(0, 0, 0, 0.5);
  font-weight: 400;
}

/* ============================================================
   登录按钮 — 磨砂玻璃深色风格
   ============================================================ */
.login-btn {
  width: 100%;
  height: 46px;
  border: none;
  border-radius: 11px;
  cursor: pointer;
  font-family: inherit;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 1.5px;
  color: #ffffff;
  position: relative;
  overflow: hidden;
  transition: all 0.2s ease;
  outline: none;

  /* 磨砂玻璃深色 */
  background: rgba(0, 0, 0, 0.84);
  backdrop-filter: blur(20px) saturate(180%);
  -webkit-backdrop-filter: blur(20px) saturate(180%);

  /* 顶部高光线 */
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 1px 3px rgba(0, 0, 0, 0.12),
    0 4px 14px rgba(0, 0, 0, 0.15);
}

.login-btn:hover:not(:disabled) {
  background: rgba(0, 0, 0, 0.72);
  transform: translateY(-0.5px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    0 2px 6px rgba(0, 0, 0, 0.14),
    0 8px 24px rgba(0, 0, 0, 0.14);
}

.login-btn:active:not(:disabled) {
  background: rgba(0, 0, 0, 0.9);
  transform: translateY(0.5px);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.05),
    0 1px 2px rgba(0, 0, 0, 0.1);
}

.login-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  transform: none;
}

.btn-text { display: block; }

.btn-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

/* ============================================================
   底部
   ============================================================ */
.card-footer {
  text-align: center;
  font-size: 12.5px;
  color: rgba(0, 0, 0, 0.35);
  margin-top: 22px;
  font-weight: 400;
}

.register-link {
  color: rgba(0, 0, 0, 0.55);
  text-decoration: none;
  font-weight: 500;
  transition: color 0.15s;
}
.register-link:hover { color: rgba(0, 0, 0, 0.8); }

/* ============================================================
   页面底部版本号
   ============================================================ */
.page-version {
  position: fixed;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 11px;
  color: rgba(0, 0, 0, 0.2);
  letter-spacing: 0.3px;
  white-space: nowrap;
  pointer-events: none;
  z-index: 5;
}

/* ============================================================
   过渡动画
   ============================================================ */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* ============================================================
   响应式
   ============================================================ */
@media (max-width: 480px) {
  .login-card {
    width: 92vw;
    padding: 32px 28px 28px;
    border-radius: 18px;
  }

  .card-title { font-size: 22px; }
}
</style>
