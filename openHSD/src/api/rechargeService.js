import { request } from './request.js'

/**
 * 获取用户绑定的 API Key 信息
 * 
 * 查询当前用户是否已绑定 OpenRouter API Key，
 * 以及 Key 的使用量和限额信息。
 * 
 * @returns {Promise<Object>} 包含绑定状态和 Key 信息的响应
 */
export async function getKeyInfo() {
  const res = await request('/recharge/key-info')
  return res.json()
}

/**
 * 绑定 OpenRouter API Key
 * 
 * 用户提交自己的 OpenRouter API Key 进行绑定，
 * 绑定成功后才能进行充值操作。
 * 
 * @param {string} apiKey - 完整的 OpenRouter API Key
 * @returns {Promise<Object>} 绑定结果
 */
export async function bindKey(apiKey) {
  const res = await request('/recharge/bind-key', {
    method: 'POST',
    body: JSON.stringify({ apiKey })
  })
  return res.json()
}

/**
 * 获取当前汇率
 * 
 * 查询美元兑人民币的汇率，用于前端金额换算显示。
 * 
 * @returns {Promise<Object>} 包含汇率信息的响应
 */
export async function getExchangeRate() {
  const res = await request('/recharge/exchange-rate')
  return res.json()
}

/**
 * 创建充值订单
 * 
 * 根据用户选择的充值金额创建微信 Native 支付订单，
 * 返回二维码链接供前端生成支付二维码。
 * 
 * @param {number} amountUsd - 充值金额（美元）
 * @returns {Promise<Object>} 包含订单号和二维码链接的响应
 */
export async function createOrder(amountUsd) {
  const res = await request('/recharge/create', {
    method: 'POST',
    body: JSON.stringify({ amountUsd })
  })
  return res.json()
}

/**
 * 查询订单状态
 * 
 * 根据订单号查询订单的当前状态，
 * 用于支付页面的轮询查询支付结果。
 * 
 * @param {string} orderNo - 商户订单号
 * @returns {Promise<Object>} 包含订单状态信息的响应
 */
export async function getOrderStatus(orderNo) {
  const res = await request(`/recharge/status/${orderNo}`)
  return res.json()
}

/**
 * 获取充值历史记录
 * 
 * 查询当前用户的充值订单历史列表，
 * 按时间倒序排列。
 * 
 * @param {number} [limit=10] - 返回记录数量限制
 * @returns {Promise<Object>} 包含订单列表的响应
 */
export async function getOrderHistory(limit = 10) {
  const res = await request(`/recharge/history?limit=${limit}`)
  return res.json()
}

/**
 * 模拟支付成功
 * 
 * 仅在模拟模式下可用，手动触发支付成功流程。
 * 用于开发测试阶段模拟支付回调。
 * 
 * @param {string} orderNo - 商户订单号
 * @returns {Promise<Object>} 处理结果响应
 */
export async function mockPaySuccess(orderNo) {
  const res = await request(`/recharge/mock/pay/${orderNo}`, {
    method: 'POST'
  })
  return res.json()
}
