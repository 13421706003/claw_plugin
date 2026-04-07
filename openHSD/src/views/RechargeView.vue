<script setup>
/**
 * 充值页面组件
 * 
 * 提供完整的充值功能界面，包括：
 * - API Key 绑定与管理
 * - OpenRouter Key 信息展示
 * - 充值金额选择与支付
 * - 支付完成/取消按钮
 * - 差值轮询查询支付状态
 * - 充值历史记录展示
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { PayCircleOutlined, KeyOutlined, DollarOutlined, SwapOutlined, HistoryOutlined, CheckCircleOutlined, WechatOutlined, AlipayOutlined } from '@ant-design/icons-vue'
import { Card, Button, InputNumber, Modal, QRCode, message, Spin, Progress, Tag } from 'ant-design-vue'
import { getKeyInfo, bindKey, createOrder, getOrderHistory, mockPaySuccess, queryOrderStatus, cancelOrder } from '../api/rechargeService.js'
import { useUserStore } from '../stores/user.js'

const userStore = useUserStore()

const loading = ref(false)
const keyInfo = ref(null)
const bound = ref(false)
const keyLabel = ref('')
const exchangeRate = ref(8.0)
const selectedAmount = ref(10)
const customAmount = ref(null)
const paymentChannel = ref('wechat')
const orderHistory = ref([])
const bindModalVisible = ref(false)
const inputApiKey = ref('')
const bindLoading = ref(false)
const payModalVisible = ref(false)
const payQrcodeUrl = ref('')
const payRedirectMode = ref(false)
const payRedirectUrl = ref('')
const payOrderNo = ref('')
const payAmountCny = ref(0)
const payAmountUsd = ref(0)
const payStatus = ref(0)
const mockMode = ref(false)

let payTimer = null
let pollIndex = 0

const POLL_INTERVALS = [1000, 2000, 5000, 10000, 20000, 40000, 60000]
const alipayOrderCreated = ref(false)
const presetAmounts = [5, 10, 20, 50, 100]

const displayAmount = computed(() => {
  return customAmount.value !== null ? customAmount.value : selectedAmount.value
})

const displayAmountCny = computed(() => {
  return (displayAmount.value * exchangeRate.value).toFixed(2)
})

const usagePercent = computed(() => {
  if (!keyInfo.value || !keyInfo.value.limit) return 0
  const limit = keyInfo.value.limit || 0
  const usage = keyInfo.value.usage || 0
  if (limit <= 0) return 0
  return Math.min(100, Math.round((usage / limit) * 100))
})

onMounted(async () => {
  await loadKeyInfo()
  await loadOrderHistory()
})

onUnmounted(() => {
  if (payTimer) {
    clearTimeout(payTimer)
  }
})

const loadKeyInfo = async () => {
  loading.value = true
  try {
    const res = await getKeyInfo()
    if (res.success) {
      bound.value = res.bound
      keyLabel.value = res.keyLabel
      keyInfo.value = res.keyInfo
    } else {
      message.error(res.message || '获取Key信息失败')
    }
  } catch (e) {
    console.error('加载Key信息失败:', e)
  } finally {
    loading.value = false
  }
}

const loadOrderHistory = async () => {
  try {
    const res = await getOrderHistory(5)
    if (res.success) {
      orderHistory.value = res.orders || []
    }
  } catch (e) {
    console.error('加载充值记录失败:', e)
  }
}

const handleBindKey = async () => {
  if (!inputApiKey.value || !inputApiKey.value.trim()) {
    message.warning('请输入 API Key')
    return
  }
  
  bindLoading.value = true
  try {
    const res = await bindKey(inputApiKey.value.trim())
    if (res.success) {
      message.success('绑定成功')
      bindModalVisible.value = false
      inputApiKey.value = ''
      await loadKeyInfo()
    } else {
      message.error(res.message || '绑定失败')
    }
  } catch (e) {
    message.error('绑定失败')
  } finally {
    bindLoading.value = false
  }
}

const selectAmount = (amount) => {
  selectedAmount.value = amount
  customAmount.value = null
}

const handleCustomChange = (val) => {
  customAmount.value = val
}

const isRedirectUrl = (url) => {
  if (!url || typeof url !== 'string') return false
  const trimmedUrl = url.trim()
  return /^https?:\/\//i.test(trimmedUrl)
}

const handleRecharge = async () => {
  if (!bound.value) {
    message.warning('请先绑定 OpenRouter API Key')
    bindModalVisible.value = true
    return
  }
  
  const amount = displayAmount.value
  if (!amount || amount < 1) {
    message.warning('最低充值金额为 1 美元')
    return
  }
  
  payOrderNo.value = ''
  payQrcodeUrl.value = ''
  payRedirectUrl.value = ''
  payRedirectMode.value = false
  alipayOrderCreated.value = false
  payStatus.value = 0
  pollIndex = 0
  
  if (paymentChannel.value === 'wechat') {
    await createWechatOrder(amount)
  } else {
    payAmountCny.value = (amount * exchangeRate.value).toFixed(2)
    payAmountUsd.value = amount
    mockMode.value = false
    payModalVisible.value = true
  }
}

const createWechatOrder = async (amount) => {
  loading.value = true
  try {
    const res = await createOrder(amount, 'wechat')
    if (res.success) {
      payQrcodeUrl.value = res.qrcodeUrl
      payOrderNo.value = res.orderNo
      payAmountCny.value = res.amountCny
      payAmountUsd.value = res.amountUsd
      mockMode.value = res.mockMode || false
      payModalVisible.value = true
    } else {
      message.error(res.message || '创建订单失败')
    }
  } catch (e) {
    message.error('创建订单失败')
  } finally {
    loading.value = false
  }
}

const createAlipayOrder = async () => {
  const amount = payAmountUsd.value
  if (!amount || amount < 1) {
    message.error('订单金额无效')
    return
  }
  
  loading.value = true
  try {
    const res = await createOrder(amount, 'ali')
    if (res.success) {
      payOrderNo.value = res.orderNo
      payQrcodeUrl.value = res.qrcodeUrl
      payRedirectUrl.value = res.qrcodeUrl
      payRedirectMode.value = !res.mockMode && isRedirectUrl(res.qrcodeUrl)
      mockMode.value = res.mockMode || false
      alipayOrderCreated.value = true
      
      if (payRedirectMode.value) {
        const openedWindow = window.open(payRedirectUrl.value, '_blank')
        if (!openedWindow) {
          message.warning('浏览器拦截了新标签页，请允许弹窗后重试')
        }
      }
    } else {
      message.error(res.message || '创建订单失败')
    }
  } catch (e) {
    message.error('创建订单失败')
  } finally {
    loading.value = false
  }
}

const startDiffPolling = () => {
  if (payTimer) {
    clearTimeout(payTimer)
    payTimer = null
  }
  
  pollIndex = 0
  runPolling()
}

const runPolling = async () => {
  if (!payOrderNo.value || payStatus.value >= 2) {
    return
  }
  
  try {
    const res = await queryOrderStatus(payOrderNo.value)
    
    if (res.success && res.status >= 2) {
      payStatus.value = res.status
      message.success('充值成功！')
      await new Promise(resolve => setTimeout(resolve, 500))
      await loadKeyInfo()
      await loadOrderHistory()
      setTimeout(() => {
        payModalVisible.value = false
      }, 1500)
    } else {
      const nextInterval = POLL_INTERVALS[Math.min(pollIndex, POLL_INTERVALS.length - 1)]
      pollIndex++
      payTimer = setTimeout(runPolling, nextInterval)
    }
  } catch (e) {
    console.error('轮询查询订单状态失败:', e)
    const nextInterval = POLL_INTERVALS[Math.min(pollIndex, POLL_INTERVALS.length - 1)]
    pollIndex++
    payTimer = setTimeout(runPolling, nextInterval)
  }
}

const handlePayComplete = async () => {
  if (!payOrderNo.value) {
    message.warning('订单未创建，请先完成支付')
    return
  }
  
  loading.value = true
  try {
    const res = await queryOrderStatus(payOrderNo.value)
    if (res.success && res.status >= 2) {
      payStatus.value = res.status
      message.success('充值成功！额度已到账')
      await new Promise(resolve => setTimeout(resolve, 500))
      await loadKeyInfo()
      await loadOrderHistory()
      setTimeout(() => {
        payModalVisible.value = false
      }, 1500)
    } else {
      message.info('未检测到支付。正在持续查询中...')
      startDiffPolling()
    }
  } catch (e) {
    message.error('查询订单状态失败')
  } finally {
    loading.value = false
  }
}

const handleCancelOrder = async () => {
  if (!payOrderNo.value) {
    payModalVisible.value = false
    return
  }
  
  loading.value = true
  try {
    await cancelOrder(payOrderNo.value)
    message.info('订单已取消')
  } catch (e) {
    message.error('取消订单失败')
  } finally {
    loading.value = false
    payModalVisible.value = false
  }
}

const handleMockPay = async () => {
  if (!payOrderNo.value) {
    message.error('订单号不存在')
    return
  }
  
  loading.value = true
  try {
    const res = await mockPaySuccess(payOrderNo.value)
    if (res.success) {
      payStatus.value = 2
      message.success('模拟支付成功！额度已到账')
      await new Promise(resolve => setTimeout(resolve, 500))
      await loadKeyInfo()
      await loadOrderHistory()
      setTimeout(() => {
        payModalVisible.value = false
      }, 1500)
    } else {
      message.error(res.message || '模拟支付失败')
    }
  } catch (e) {
    message.error('模拟支付失败')
  } finally {
    loading.value = false
  }
}

const formatTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

const getStatusTag = (status) => {
  const map = {
    0: { color: 'orange', text: '待支付' },
    1: { color: 'blue', text: '已支付' },
    2: { color: 'green', text: '已完成' },
    3: { color: 'default', text: '已关闭' }
  }
  return map[status] || { color: 'default', text: '未知' }
}
</script>

<template>
  <div class="recharge-view">
    <div class="page-header">
      <PayCircleOutlined class="header-icon" />
      <div class="header-content">
        <h2 class="header-title">充值中心</h2>
        <p class="header-desc">账户余额与充值服务</p>
      </div>
    </div>
    
    <div class="page-content">
      <Spin :spinning="loading">
        <div class="main-layout">
          <div class="left-panel">
            <Card class="info-card">
              <template #title>
                <span class="card-title"><KeyOutlined /> API Key 状态</span>
              </template>
              <div v-if="bound" class="key-info">
                <div class="key-label">{{ keyLabel }}</div>
                <Button type="link" size="small" @click="bindModalVisible = true">更换</Button>
              </div>
              <div v-else class="key-unbound">
                <p class="unbound-text">尚未绑定 API Key</p>
                <Button type="primary" size="small" @click="bindModalVisible = true">立即绑定</Button>
              </div>
            </Card>
            
            <Card class="info-card">
              <template #title>
                <span class="card-title"><DollarOutlined /> 账户余额</span>
              </template>
              <div v-if="bound && keyInfo" class="balance-info">
                <div class="balance-row">
                  <span class="balance-label">限额</span>
                  <span class="balance-value">${{ keyInfo.limit?.toFixed(2) || '0.00' }}</span>
                </div>
                <div class="balance-row">
                  <span class="balance-label">已用</span>
                  <span class="balance-value used">${{ keyInfo.usage?.toFixed(2) || '0.00' }}</span>
                </div>
                <div class="balance-row">
                  <span class="balance-label">剩余</span>
                  <span class="balance-value remaining">${{ keyInfo.limitRemaining?.toFixed(2) || '0.00' }}</span>
                </div>
                <Progress :percent="usagePercent" :showInfo="false" strokeColor="#52c41a" />
              </div>
              <div v-else class="no-data">-</div>
            </Card>
            
            <Card class="info-card">
              <template #title>
                <span class="card-title"><SwapOutlined /> 实时汇率</span>
              </template>
              <div class="rate-info">
                <div class="rate-value">1 USD = {{ exchangeRate }} CNY</div>
                <div class="rate-note">固定汇率</div>
              </div>
            </Card>
            
            <Card class="info-card">
              <template #title>
                <span class="card-title">📊 用量统计</span>
              </template>
              <div v-if="bound && keyInfo" class="usage-info">
                <div class="usage-row">
                  <span>今日</span>
                  <span>${{ keyInfo.usageDaily?.toFixed(2) || '0.00' }}</span>
                </div>
                <div class="usage-row">
                  <span>本周</span>
                  <span>${{ keyInfo.usageWeekly?.toFixed(2) || '0.00' }}</span>
                </div>
                <div class="usage-row">
                  <span>本月</span>
                  <span>${{ keyInfo.usageMonthly?.toFixed(2) || '0.00' }}</span>
                </div>
              </div>
              <div v-else class="no-data">-</div>
            </Card>
          </div>
          
          <div class="right-panel">
            <Card class="recharge-card">
              <template #title>
                <span class="card-title">💳 选择充值金额</span>
              </template>
              
              <div class="amount-section">
                <div class="section-label">支付方式</div>
                <div class="payment-channels">
                  <div
                    :class="['channel-item', { active: paymentChannel === 'wechat' }]"
                    @click="paymentChannel = 'wechat'"
                  >
                    <WechatOutlined class="channel-icon wechat" />
                    <span class="channel-name">微信支付</span>
                  </div>
                  <div
                    :class="['channel-item', { active: paymentChannel === 'ali' }]"
                    @click="paymentChannel = 'ali'"
                  >
                    <AlipayOutlined class="channel-icon alipay" />
                    <span class="channel-name">支付宝</span>
                  </div>
                </div>
                
                <div class="section-label">充值金额</div>
                <div class="preset-amounts">
                  <div
                    v-for="amount in presetAmounts"
                    :key="amount"
                    :class="['amount-item', { active: selectedAmount === amount && customAmount === null }]"
                    @click="selectAmount(amount)"
                  >
                    <div class="amount-usd">${{ amount }}</div>
                    <div class="amount-cny">¥{{ amount * exchangeRate }}</div>
                  </div>
                </div>
                
                <div class="custom-amount">
                  <span class="custom-label">自定义金额</span>
                  <div class="custom-input">
                    <InputNumber
                      v-model:value="customAmount"
                      :min="1"
                      :max="10000"
                      :precision="2"
                      placeholder="输入金额"
                      style="width: 150px"
                      @change="handleCustomChange"
                    />
                    <span class="unit">USD</span>
                    <span class="equal">=</span>
                    <span class="cny-value">¥{{ displayAmountCny }}</span>
                  </div>
                </div>
                
                <div class="recharge-summary">
                  <div class="summary-row">
                    <span>充值金额</span>
                    <span class="summary-value">${{ displayAmount }}</span>
                  </div>
                  <div class="summary-row">
                    <span>应付金额</span>
                    <span class="summary-value highlight">¥{{ displayAmountCny }}</span>
                  </div>
                  <div class="summary-row">
                    <span>支付方式</span>
                    <span class="summary-value">{{ paymentChannel === 'wechat' ? '微信支付' : '支付宝' }}</span>
                  </div>
                </div>
                
                <div class="recharge-action">
                  <Button type="primary" size="large" :loading="loading" @click="handleRecharge">
                    立即充值
                  </Button>
                </div>
              </div>
            </Card>
          </div>
        </div>
        
        <Card class="history-card">
          <template #title>
            <span class="card-title"><HistoryOutlined /> 充值记录</span>
          </template>
          
          <div v-if="orderHistory.length > 0" class="history-list">
            <div v-for="order in orderHistory" :key="order.orderNo" class="history-item">
              <div class="history-left">
                <div class="history-amount">
                  <span class="usd">${{ order.amountUsd }}</span>
                  <span class="cny">¥{{ order.amountCny }}</span>
                </div>
                <div class="history-time">{{ formatTime(order.createdAt) }}</div>
              </div>
              <div class="history-right">
                <Tag :color="getStatusTag(order.status).color">{{ getStatusTag(order.status).text }}</Tag>
              </div>
            </div>
          </div>
          <div v-else class="no-history">暂无充值记录</div>
        </Card>
      </Spin>
    </div>
    
    <Modal
      v-model:open="bindModalVisible"
      title="绑定 OpenRouter API Key"
      :confirmLoading="bindLoading"
      @ok="handleBindKey"
    >
      <div class="bind-modal-content">
        <p class="bind-tip">请输入您的 OpenRouter API Key，用于充值和管理额度。</p>
        <a-input
          v-model:value="inputApiKey"
          placeholder="sk-or-v1-xxxxxxxx"
          size="large"
        />
        <p class="bind-note">
          您可以在 <a href="https://openrouter.ai/keys" target="_blank">OpenRouter Keys</a> 页面创建 API Key
        </p>
      </div>
    </Modal>
    
    <Modal
      v-model:open="payModalVisible"
      :title="mockMode ? '模拟支付' : (paymentChannel === 'wechat' ? '微信支付' : '支付宝支付')"
      :footer="null"
      :closable="payStatus < 2"
      :maskClosable="false"
    >
      <div class="pay-modal-content">
        <div v-if="payStatus < 2" class="pay-qrcode">
          <template v-if="paymentChannel === 'wechat'">
            <QRCode v-if="payQrcodeUrl" :value="payQrcodeUrl" :size="200" />
            <p class="pay-tip">{{ mockMode ? '模拟支付模式' : '请使用微信扫码支付' }}</p>
          </template>
          
          <template v-else>
            <template v-if="!alipayOrderCreated">
              <p class="pay-tip">请点击下方按钮跳转支付宝完成支付</p>
              <div class="redirect-pay-action">
                <Button type="primary" size="large" :loading="loading" @click="createAlipayOrder">
                  打开支付宝支付页面
                </Button>
              </div>
            </template>
            <template v-else>
              <QRCode v-if="payQrcodeUrl && !payRedirectMode" :value="payQrcodeUrl" :size="200" />
              <p class="pay-tip" v-if="payRedirectMode">已打开支付宝支付页面，请在完成支付后点击下方"支付完成"</p>
              <p class="pay-tip" v-else>{{ mockMode ? '模拟支付模式' : '请使用支付宝扫码支付' }}</p>
            </template>
          </template>
          
          <div class="pay-info">
            <div class="pay-amount">
              <span class="label">支付金额：</span>
              <span class="value">¥{{ payAmountCny }}</span>
            </div>
            <div class="pay-order" v-if="payOrderNo">
              <span class="label">订单号：</span>
              <span class="value">{{ payOrderNo }}</span>
            </div>
          </div>
          
          <div v-if="mockMode" class="mock-pay-action">
            <Button type="primary" size="large" :loading="loading" @click="handleMockPay">
              模拟支付成功
            </Button>
            <p class="mock-tip">测试模式：点击按钮模拟支付成功</p>
          </div>
          
          <div class="pay-actions" v-if="!mockMode && (paymentChannel === 'wechat' || alipayOrderCreated)">
            <Button type="primary" size="large" :loading="loading" @click="handlePayComplete">
              支付完成
            </Button>
            <Button size="large" :loading="loading" @click="handleCancelOrder">
              已取消
            </Button>
          </div>
        </div>
        <div v-else class="pay-success">
          <CheckCircleOutlined class="success-icon" />
          <p class="success-text">支付成功！额度已到账</p>
        </div>
      </div>
    </Modal>
  </div>
</template>

<style scoped>
.recharge-view {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid #f0f0f0;
  background: #fff;
  flex-shrink: 0;
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
  overflow: auto;
  padding: 24px;
  display: flex;
  flex-direction: column;
}

.main-layout {
  display: flex;
  gap: 24px;
  flex: 1;
  min-height: 0;
}

.left-panel {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.right-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.info-card {
  background: #fff;
  border-radius: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #333;
}

.key-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.key-label {
  font-family: monospace;
  font-size: 13px;
  color: #666;
}

.key-unbound {
  text-align: center;
}

.unbound-text {
  color: #999;
  margin-bottom: 8px;
}

.balance-info {
  padding: 4px 0;
}

.balance-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.balance-label {
  color: #666;
  font-size: 13px;
}

.balance-value {
  font-weight: 500;
  font-size: 14px;
}

.balance-value.used {
  color: #ff4d4f;
}

.balance-value.remaining {
  color: #52c41a;
}

.rate-info {
  text-align: center;
  padding: 8px 0;
}

.rate-value {
  font-size: 20px;
  font-weight: 600;
  color: #1677ff;
}

.rate-note {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.usage-info {
  padding: 4px 0;
}

.usage-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
  font-size: 13px;
}

.no-data {
  text-align: center;
  color: #ccc;
  padding: 16px 0;
}

.recharge-card {
  background: #fff;
  border-radius: 12px;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.recharge-card :deep(.ant-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.amount-section {
  display: flex;
  flex-direction: column;
  gap: 20px;
  flex: 1;
}

.section-label {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  margin-bottom: 12px;
}

.payment-channels {
  display: flex;
  gap: 16px;
}

.channel-item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 16px 24px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fafafa;
}

.channel-item:hover {
  border-color: #1677ff;
  background: #f0f7ff;
}

.channel-item.active {
  border-color: #1677ff;
  background: #e6f4ff;
}

.channel-icon {
  font-size: 24px;
}

.channel-icon.wechat {
  color: #07c160;
}

.channel-icon.alipay {
  color: #1677ff;
}

.channel-name {
  font-size: 15px;
  font-weight: 500;
  color: #333;
}

.preset-amounts {
  display: flex;
  gap: 12px;
}

.amount-item {
  flex: 1;
  padding: 16px;
  border: 2px solid #e8e8e8;
  border-radius: 12px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
}

.amount-item:hover {
  border-color: #1677ff;
  background: #f0f7ff;
}

.amount-item.active {
  border-color: #1677ff;
  background: #e6f4ff;
}

.amount-usd {
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.amount-cny {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.custom-amount {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.custom-label {
  font-size: 14px;
  color: #666;
}

.custom-input {
  display: flex;
  align-items: center;
  gap: 8px;
}

.unit {
  font-size: 14px;
  color: #666;
}

.equal {
  color: #999;
}

.cny-value {
  font-size: 16px;
  font-weight: 500;
  color: #ff4d4f;
}

.recharge-summary {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  margin-top: auto;
}

.summary-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 14px;
  color: #666;
}

.summary-row:not(:last-child) {
  border-bottom: 1px solid #e8e8e8;
}

.summary-value {
  font-weight: 500;
  color: #333;
}

.summary-value.highlight {
  font-size: 18px;
  color: #ff4d4f;
}

.recharge-action {
  text-align: center;
  padding-top: 20px;
}

.recharge-action .ant-btn {
  min-width: 200px;
  height: 48px;
  font-size: 16px;
}

.history-card {
  background: #fff;
  border-radius: 12px;
  margin-top: 24px;
  flex-shrink: 0;
}

.history-list {
  padding: 4px 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fafafa;
  border-radius: 8px;
}

.history-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.history-amount .usd {
  font-size: 16px;
  font-weight: 500;
  color: #333;
}

.history-amount .cny {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

.history-time {
  font-size: 12px;
  color: #999;
}

.no-history {
  text-align: center;
  color: #999;
  padding: 24px 0;
}

.bind-modal-content {
  padding: 8px 0;
}

.bind-tip {
  margin-bottom: 16px;
  color: #666;
}

.bind-note {
  margin-top: 12px;
  font-size: 12px;
  color: #999;
}

.pay-modal-content {
  padding: 16px 0;
  text-align: center;
}

.pay-qrcode {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.pay-tip {
  margin-top: 16px;
  font-size: 14px;
  color: #666;
}

.pay-info {
  margin-top: 16px;
  padding: 12px 24px;
  background: #fafafa;
  border-radius: 8px;
  text-align: left;
}

.pay-info .pay-amount,
.pay-info .pay-order {
  margin-bottom: 8px;
}

.pay-info .label {
  color: #666;
  font-size: 13px;
}

.pay-info .value {
  font-weight: 500;
}

.pay-info .pay-amount .value {
  font-size: 18px;
  color: #ff4d4f;
}

.pay-actions {
  margin-top: 24px;
  display: flex;
  gap: 16px;
  justify-content: center;
}

.pay-success {
  padding: 24px 0;
}

.success-icon {
  font-size: 64px;
  color: #52c41a;
}

.success-text {
  margin-top: 16px;
  font-size: 16px;
  color: #333;
}

.mock-pay-action {
  margin-top: 24px;
  text-align: center;
}

.redirect-pay-action {
  margin-top: 16px;
}

.mock-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #faad14;
}

@media (max-width: 1024px) {
  .main-layout {
    flex-direction: column;
  }
  
  .left-panel {
    width: 100%;
    flex-direction: row;
    flex-wrap: wrap;
  }
  
  .left-panel .info-card {
    flex: 1;
    min-width: 200px;
  }
  
  .payment-channels {
    flex-wrap: wrap;
  }
  
  .channel-item {
    min-width: calc(50% - 8px);
  }
}

@media (max-width: 640px) {
  .page-content {
    padding: 16px;
  }
  
  .preset-amounts {
    flex-wrap: wrap;
  }
  
  .amount-item {
    min-width: calc(33% - 8px);
  }
  
  .payment-channels {
    flex-direction: column;
  }
  
  .channel-item {
    min-width: 100%;
  }
}
</style>
