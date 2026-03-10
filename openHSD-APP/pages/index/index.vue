<template>
  <view class="splash">
    <view class="splash-logo">
      <view class="logo-icon">
        <image
          src="https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original"
          mode="aspectFit"
          style="width: 40rpx; height: 40rpx;"
        />
      </view>
      <view class="logo-text">
        <text class="logo-name">OPENHSD</text>
        <text class="logo-sub">GATEWAY DASHBOARD</text>
      </view>
    </view>
    <view class="splash-spinner"></view>
  </view>
</template>

<script setup>
import { onMounted } from 'vue'
import { storage } from '../../utils/storage.js'

onMounted(() => {
  setTimeout(() => {
    const token = storage.getToken()
    if (token) {
      uni.reLaunch({ url: '/pages/chat/chat' })
    } else {
      uni.reLaunch({ url: '/pages/login/login' })
    }
  }, 600)
})
</script>

<style scoped>
.splash {
  width: 100vw;
  height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  gap: 48rpx;
}

.splash-logo {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.logo-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 18rpx;
  background: #f2f2f7;
  display: flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.logo-name {
  font-size: 30rpx;
  font-weight: 700;
  color: rgba(0, 0, 0, 0.85);
  letter-spacing: 4rpx;
  line-height: 1;
}

.logo-sub {
  font-size: 20rpx;
  font-weight: 500;
  color: rgba(0, 0, 0, 0.3);
  letter-spacing: 2rpx;
  line-height: 1;
}

.splash-spinner {
  width: 48rpx;
  height: 48rpx;
  border: 3rpx solid rgba(0, 0, 0, 0.08);
  border-top-color: #ff4d4f;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
</style>
