<script setup>
import { PayCircleOutlined } from '@ant-design/icons-vue'
</script>

<template>
  <div class="recharge-view">
    <div class="page-header">
      <PayCircleOutlined class="header-icon" />
      <div class="header-content">
        <h2 class="header-title">充值</h2>
        <p class="header-desc">账户余额与充值服务</p>
      </div>
    </div>
    <div class="page-content">
      <div class="empty-state">
        <p>充值功能开发中...</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.recharge-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
}

.header-icon {
  font-size: 28px;
  color: #52c41a;
}

.header-title {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
}

.header-desc {
  margin: 0;
  color: #999;
  font-size: 13px;
}

.page-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: auto;
}

.empty-state {
  text-align: center;
  color: #bbb;
  font-size: 14px;
}
</style>
