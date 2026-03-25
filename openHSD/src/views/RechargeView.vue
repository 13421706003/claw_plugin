<script setup>
/**
 * 充值页面组件
 * 
 * 提供完整的充值功能界面，包括：
 * - API Key 绑定与管理
 * - OpenRouter Key 信息展示
 * - 充值金额选择与支付
 * - 支付状态轮询
 * - 充值历史记录展示
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { PayCircleOutlined, KeyOutlined, DollarOutlined, SwapOutlined, HistoryOutlined, QrcodeOutlined, CheckCircleOutlined } from '@ant-design/icons-vue'
import { Card, Button, InputNumber, Modal, QRCode, message, Spin, Progress, Tag } from 'ant-design-vue'
import { getKeyInfo, bindKey, createOrder, getOrderStatus, getOrderHistory, mockPaySuccess } from '../api/rechargeService.js'
import { useUserStore } from '../stores/user.js'

const userStore = useUserStore()

// ==================== 状态定义 ====================

/** 页面加载状态 */
const loading = ref(false)

/** API Key 详细信息 */
const keyInfo = ref(null)

/** Key 是否已绑定 */
const bound = ref(false)

/** Key 脱敏标签 */
const keyLabel = ref('')

/** 当前汇率 */
const exchangeRate = ref(8.0)

/** 选中的预设金额 */
const selectedAmount = ref(10)

/** 自定义输入金额 */
const customAmount = ref(null)

/** 充值历史记录列表 */
const orderHistory = ref([])

/** API Key 绑定弹窗显示状态 */
const bindModalVisible = ref(false)

/** API Key 输入框内容 */
const inputApiKey = ref('')

/** 绑定按钮加载状态 */
const bindLoading = ref(false)

/** 支付弹窗显示状态 */
const payModalVisible = ref(false)

/** 支付二维码链接 */
const payQrcodeUrl = ref('')

/** 当前支付订单号 */
const payOrderNo = ref('')

/** 支付金额（人民币） */
const payAmountCny = ref(0)

/** 支付金额（美元） */
const payAmountUsd = ref(0)

/** 支付订单状态 */
const payStatus = ref(0)

/** 支付倒计时（秒） */
const payCountdown = ref(300)

/** 是否为模拟模式 */
const mockMode = ref(false)

/** 支付轮询定时器 */
let payTimer = null

// ==================== 常量定义 ====================

/** 预设充值金额选项（美元） */
const presetAmounts = [5, 10, 20, 50, 100]

// ==================== 计算属性 ====================

/**
 * 当前显示的充值金额
 * 
 * 如果用户输入了自定义金额则使用自定义值，
 * 否则使用预设金额。
 */
const displayAmount = computed(() => {
  return customAmount.value !== null ? customAmount.value : selectedAmount.value
})

/**
 * 当前显示金额对应的人民币金额
 */
const displayAmountCny = computed(() => {
  return (displayAmount.value * exchangeRate.value).toFixed(2)
})

/**
 * API Key 使用量百分比
 * 
 * 计算已用额度占总限额的比例，用于进度条展示。
 */
const usagePercent = computed(() => {
  if (!keyInfo.value || !keyInfo.value.limit) return 0
  const limit = keyInfo.value.limit || 0
  const usage = keyInfo.value.usage || 0
  if (limit <= 0) return 0
  return Math.min(100, Math.round((usage / limit) * 100))
})

// ==================== 生命周期钩子 ====================

/**
 * 组件挂载时加载数据
 */
onMounted(async () => {
  await loadKeyInfo()
  await loadOrderHistory()
})

/**
 * 组件卸载时清理定时器
 */
onUnmounted(() => {
  if (payTimer) {
    clearInterval(payTimer)
  }
})

// ==================== 数据加载方法 ====================

/**
 * 加载 API Key 信息
 * 
 * 从后端获取用户绑定的 Key 信息，
 * 包括绑定状态、使用量、限额等。
 */
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

/**
 * 加载充值历史记录
 * 
 * 从后端获取用户的充值订单历史，
 * 用于在页面底部展示最近充值记录。
 */
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

// ==================== API Key 绑定方法 ====================

/**
 * 处理 API Key 绑定
 * 
 * 验证用户输入的 Key 并提交到后端进行绑定。
 * 绑定成功后刷新 Key 信息显示。
 */
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

// ==================== 充值金额选择方法 ====================

/**
 * 选择预设充值金额
 * 
 * 用户点击预设金额卡片时调用，
 * 清除自定义金额并设置选中的预设值。
 * 
 * @param {number} amount - 预设金额值
 */
const selectAmount = (amount) => {
  selectedAmount.value = amount
  customAmount.value = null
}

/**
 * 处理自定义金额输入变化
 * 
 * 用户在自定义输入框中输入金额时调用。
 * 
 * @param {number} val - 输入的金额值
 */
const handleCustomChange = (val) => {
  customAmount.value = val
}

// ==================== 充值支付方法 ====================

/**
 * 处理充值按钮点击
 * 
 * 验证用户状态和充值金额，
 * 创建支付订单并启动支付轮询。
 */
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
  
  loading.value = true
  try {
    const res = await createOrder(amount)
    if (res.success) {
      payQrcodeUrl.value = res.qrcodeUrl
      payOrderNo.value = res.orderNo
      payAmountCny.value = res.amountCny
      payAmountUsd.value = res.amountUsd
      payStatus.value = 0
      payCountdown.value = 300
      mockMode.value = res.mockMode || false
      payModalVisible.value = true
      if (!mockMode.value) {
        startPayPolling()
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

/**
 * 启动支付状态轮询
 * 
 * 创建定时器每 3 秒查询一次订单状态，
 * 直到支付成功或超时。
 */
const startPayPolling = () => {
  if (payTimer) {
    clearInterval(payTimer)
  }
  
  payTimer = setInterval(async () => {
    payCountdown.value--
    
    if (payCountdown.value <= 0) {
      clearInterval(payTimer)
      payModalVisible.value = false
      message.warning('支付超时，请重新下单')
      return
    }
    
    try {
      const res = await getOrderStatus(payOrderNo.value)
      if (res.success && res.status >= 2) {
        clearInterval(payTimer)
        payStatus.value = res.status
        message.success('充值成功！')
        await loadKeyInfo()
        await loadOrderHistory()
        setTimeout(() => {
          payModalVisible.value = false
        }, 2000)
      }
    } catch (e) {
      console.error('查询订单状态失败:', e)
    }
  }, 3000)
}

/**
 * 处理模拟支付
 * 
 * 在模拟模式下，用户点击"模拟支付成功"按钮触发此方法，
 * 直接调用后端接口完成支付流程。
 */
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

// ==================== 工具方法 ====================

/**
 * 格式化时间显示
 * 
 * 将后端返回的时间字符串格式化为本地化显示。
 * 
 * @param {string} dateStr - ISO 格式的时间字符串
 * @returns {string} 本地化格式的时间字符串
 */
const formatTime = (dateStr) => {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN')
}

/**
 * 获取订单状态的标签配置
 * 
 * 根据状态码返回对应的颜色和文本。
 * 
 * @param {number} status - 订单状态码
 * @returns {Object} 包含 color 和 text 属性的对象
 */
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
        <div class="cards-container">
          <div class="cards-row">
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
          
          <Card class="recharge-card">
            <template #title>
              <span class="card-title">💳 选择充值金额</span>
            </template>
            
            <div class="amount-section">
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
              
              <div class="recharge-action">
                <Button type="primary" size="large" :loading="loading" @click="handleRecharge">
                  立即充值
                </Button>
              </div>
            </div>
          </Card>
          
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
        </div>
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
      :title="mockMode ? '模拟支付' : '微信支付'"
      :footer="null"
      :closable="payStatus < 2"
      :maskClosable="false"
    >
      <div class="pay-modal-content">
        <div v-if="payStatus < 2" class="pay-qrcode">
          <QRCode :value="payQrcodeUrl" :size="200" />
          <p class="pay-tip">{{ mockMode ? '模拟支付模式' : '请使用微信扫码支付' }}</p>
          <div class="pay-info">
            <div class="pay-amount">
              <span class="label">支付金额：</span>
              <span class="value">¥{{ payAmountCny }}</span>
            </div>
            <div class="pay-order">
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
          <div v-else class="pay-countdown">
            <span>等待支付中... ({{ Math.floor(payCountdown / 60) }}:{{ String(payCountdown % 60).padStart(2, '0') }})</span>
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
}

.cards-container {
  max-width: 1200px;
  margin: 0 auto;
}

.cards-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
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
  margin-bottom: 16px;
}

.amount-section {
  padding: 8px 0;
}

.preset-amounts {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
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
  margin-bottom: 24px;
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

.recharge-action {
  text-align: center;
}

.history-card {
  background: #fff;
  border-radius: 12px;
}

.history-list {
  padding: 4px 0;
}

.history-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.history-item:last-child {
  border-bottom: none;
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

.pay-countdown {
  margin-top: 16px;
  font-size: 13px;
  color: #999;
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

.mock-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #faad14;
}

@media (max-width: 1024px) {
  .cards-row {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .cards-row {
    grid-template-columns: 1fr;
  }
  
  .preset-amounts {
    flex-wrap: wrap;
  }
  
  .amount-item {
    min-width: calc(33% - 8px);
  }
}
</style>
