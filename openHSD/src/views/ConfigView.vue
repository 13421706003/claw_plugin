<script setup>
import { SettingOutlined } from '@ant-design/icons-vue'
</script>

<template>
  <div class="config-view">
    <div class="page-header">
      <SettingOutlined class="header-icon" />
      <div class="header-content">
        <h2 class="header-title">配置</h2>
        <p class="header-desc">系统配置与参数设置</p>
      </div>
    </div>
    <div class="page-content">
      <div class="empty-state">
        <p>配置功能开发中...</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.config-view {
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
  color: #1677ff;
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
