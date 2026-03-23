<script setup>
import { ref, provide } from 'vue'
import SideNav from '../components/SideNav.vue'

const collapsed = ref(false)

const setCollapsed = (val) => {
  collapsed.value = val
}

provide('navCollapsed', collapsed)
</script>

<template>
  <div class="layout-container">
    <SideNav />
    <div class="layout-content">
      <router-view />
    </div>
  </div>
</template>

<style scoped>
.layout-container {
  width: 100%;
  height: 100vh;
  display: flex;
  overflow: hidden;
  background: #f5f5f5;
}

.layout-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #fff;
}
</style>
