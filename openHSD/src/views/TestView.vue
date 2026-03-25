<template>
  <div class="test-view">
    <div class="page-header">
      <h2>接口测试</h2>
      <a-tag color="blue">{{ baseUrl || '本地' }}</a-tag>
    </div>

    <div class="test-container">
      <div class="api-list">
        <div v-for="group in apiGroups" :key="group.key" class="api-group">
          <div class="group-title">{{ group.label }}</div>
          <div
            v-for="api in group.apis"
            :key="api.key"
            class="api-item"
            :class="{ active: selectedApi?.key === api.key }"
            @click="selectApi(api)"
          >
            <a-tag :color="getMethodColor(api.method)" size="small">{{ api.method }}</a-tag>
            <span class="api-name">{{ api.name }}</span>
          </div>
        </div>
      </div>

      <div class="api-detail">
        <template v-if="selectedApi">
          <div class="detail-header">
            <a-tag :color="getMethodColor(selectedApi.method)">{{ selectedApi.method }}</a-tag>
            <span class="detail-path">{{ selectedApi.path }}</span>
          </div>

          <div class="detail-desc">{{ selectedApi.desc }}</div>

          <div v-if="selectedApi.params?.length" class="params-section">
            <div class="section-title">参数</div>
            <div class="params-grid">
              <div v-for="param in selectedApi.params" :key="param.key" class="param-item">
                <label>{{ param.label }}</label>
                <a-input
                  v-if="param.type !== 'number' && param.type !== 'file'"
                  v-model:value="paramValues[param.key]"
                  :placeholder="param.placeholder"
                />
                <a-input-number
                  v-else-if="param.type === 'number'"
                  v-model:value="paramValues[param.key]"
                  :placeholder="param.placeholder"
                  :min="param.min"
                  :max="param.max"
                  style="width: 100%"
                />
                <input
                  v-else
                  type="file"
                  :accept="param.accept"
                  @change="(e) => paramValues[param.key] = e.target.files[0]"
                  class="file-input"
                />
              </div>
            </div>
          </div>

          <div class="action-section">
            <a-button type="primary" size="large" @click="executeApi" :loading="loading">
              执行请求
            </a-button>
          </div>

          <div v-if="response !== null" class="response-section">
            <div class="section-title">
              响应
              <a-tag :color="responseColor">{{ responseStatus }}</a-tag>
            </div>
            <div class="response-body" :class="responseClass">
              <pre>{{ formattedResponse }}</pre>
            </div>
          </div>
        </template>

        <div v-else class="empty-state">
          <BugOutlined class="empty-icon" />
          <p>选择左侧接口开始测试</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { message } from 'ant-design-vue'
import { BugOutlined } from '@ant-design/icons-vue'
import { useUserStore } from '../stores/user.js'

const userStore = useUserStore()
const baseUrl = import.meta.env.VITE_API_BASE_URL || ''

const selectedApi = ref(null)
const paramValues = reactive({})
const loading = ref(false)
const response = ref(null)
const responseStatus = ref('')
const responseColor = ref('')
const responseClass = ref('')

const getMethodColor = (method) => {
  const colors = {
    GET: 'blue',
    POST: 'green',
    PUT: 'orange',
    DELETE: 'red',
    PATCH: 'purple'
  }
  return colors[method] || 'default'
}

const apiGroups = [
  {
    key: 'auth',
    label: 'Auth 认证',
    apis: [
      {
        key: 'auth-login',
        name: '登录',
        method: 'POST',
        path: '/api/auth/login',
        desc: '用户登录，返回JWT Token',
        params: [
          { key: 'username', label: '用户名', placeholder: '请输入用户名', type: 'text' },
          { key: 'password', label: '密码', placeholder: '请输入密码', type: 'text' }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/auth/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: params.username, password: params.password })
          })
          const data = await safeJson(res)
          if (data.success) {
            userStore.setToken(data.token)
            userStore.setUser(data.user)
          }
          return { status: res.status, data }
        }
      },
      {
        key: 'auth-register',
        name: '注册',
        method: 'POST',
        path: '/api/auth/register',
        desc: '用户注册',
        params: [
          { key: 'username', label: '用户名', placeholder: '请输入用户名', type: 'text' },
          { key: 'password', label: '密码', placeholder: '请输入密码', type: 'text' }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/auth/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: params.username, password: params.password })
          })
          return { status: res.status, data: await safeJson(res) }
        }
      }
    ]
  },
  {
    key: 'recharge',
    label: 'Recharge 充值',
    apis: [
      {
        key: 'recharge-key-info',
        name: '获取Key信息',
        method: 'GET',
        path: '/api/recharge/key-info',
        desc: '获取当前用户绑定的OpenRouter API Key信息',
        params: [],
        execute: async () => {
          const res = await fetch(`${baseUrl}/api/recharge/key-info`, { headers: getHeaders() })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-bind-key',
        name: '绑定Key',
        method: 'POST',
        path: '/api/recharge/bind-key',
        desc: '绑定OpenRouter API Key',
        params: [
          { key: 'apiKey', label: 'API Key', placeholder: 'sk-or-xxx', type: 'text' }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/recharge/bind-key`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ apiKey: params.apiKey })
          })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-exchange-rate',
        name: '获取汇率',
        method: 'GET',
        path: '/api/recharge/exchange-rate',
        desc: '获取当前美元兑人民币汇率',
        params: [],
        execute: async () => {
          const res = await fetch(`${baseUrl}/api/recharge/exchange-rate`, { headers: getHeaders() })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-create',
        name: '创建订单',
        method: 'POST',
        path: '/api/recharge/create',
        desc: '创建充值订单',
        params: [
          { key: 'amountUsd', label: '金额(USD)', placeholder: '5', type: 'number', min: 1 }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/recharge/create`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ amountUsd: Number(params.amountUsd) || 5 })
          })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-status',
        name: '查询订单状态',
        method: 'GET',
        path: '/api/recharge/status/{orderNo}',
        desc: '根据订单号查询订单状态',
        params: [
          { key: 'orderNo', label: '订单号', placeholder: 'RC2026...', type: 'text' }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/recharge/status/${params.orderNo}`, { headers: getHeaders() })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-history',
        name: '订单历史',
        method: 'GET',
        path: '/api/recharge/history',
        desc: '获取用户充值历史记录',
        params: [
          { key: 'limit', label: '数量', placeholder: '10', type: 'number', min: 1, max: 50 }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/recharge/history?limit=${params.limit || 10}`, { headers: getHeaders() })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-mock-pay',
        name: '模拟支付',
        method: 'POST',
        path: '/api/recharge/mock/pay/{orderNo}',
        desc: '模拟支付成功（仅开发模式）',
        params: [
          { key: 'orderNo', label: '订单号', placeholder: 'RC2026...', type: 'text' }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/recharge/mock/pay/${params.orderNo}`, {
            method: 'POST',
            headers: getHeaders()
          })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-test-list-keys',
        name: '列出所有Key',
        method: 'GET',
        path: '/api/recharge/test/list-keys',
        desc: '列出通过Management API创建的所有Key（仅开发模式）',
        params: [],
        execute: async () => {
          const res = await fetch(`${baseUrl}/api/recharge/test/list-keys`, { headers: getHeaders() })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-test-create-key',
        name: '创建子Key',
        method: 'POST',
        path: '/api/recharge/test/create-key',
        desc: '创建新的子API Key（仅开发模式）',
        params: [
          { key: 'name', label: '名称', placeholder: 'TestKey', type: 'text' },
          { key: 'limit', label: '限额', placeholder: '10', type: 'number', min: 0 }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/recharge/test/create-key?name=${encodeURIComponent(params.name || 'TestKey')}&limit=${params.limit || 10}`, {
            method: 'POST',
            headers: getHeaders()
          })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'recharge-test-update-limit',
        name: '更新Key额度',
        method: 'PATCH',
        path: '/api/recharge/test/update-limit',
        desc: '更新指定Key的消费限额，hash从"列出所有Key"获取（仅开发模式）',
        params: [
          { key: 'keyHash', label: 'Key Hash', placeholder: '325b04e71dac...', type: 'text' },
          { key: 'limit', label: '新额度', placeholder: '100', type: 'number', min: 0 }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/recharge/test/update-limit?keyHash=${encodeURIComponent(params.keyHash)}&limit=${params.limit}`, {
            method: 'PATCH',
            headers: getHeaders()
          })
          return { status: res.status, data: await safeJson(res) }
        }
      }
    ]
  },
  {
    key: 'claw',
    label: 'Claw 机器',
    apis: [
      {
        key: 'claw-status',
        name: '获取机器状态',
        method: 'GET',
        path: '/api/claw/status',
        desc: '获取用户在线机器状态',
        params: [],
        execute: async () => {
          const userId = userStore.user.value?.userId
          const res = await fetch(`${baseUrl}/api/claw/status?userId=${userId}`, { headers: getHeaders() })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'claw-send',
        name: '发送消息',
        method: 'POST',
        path: '/api/claw/send',
        desc: '向指定机器发送消息',
        params: [
          { key: 'clawId', label: '机器ID', placeholder: 'claw-xxx', type: 'text' },
          { key: 'message', label: '消息', placeholder: '消息内容', type: 'text' }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/claw/send`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ clawId: params.clawId, message: params.message })
          })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'claw-broadcast',
        name: '广播消息',
        method: 'POST',
        path: '/api/claw/broadcast',
        desc: '向所有在线机器广播消息',
        params: [
          { key: 'message', label: '消息', placeholder: '消息内容', type: 'text' }
        ],
        execute: async (params) => {
          const res = await fetch(`${baseUrl}/api/claw/broadcast`, {
            method: 'POST',
            headers: getHeaders(),
            body: JSON.stringify({ message: params.message })
          })
          return { status: res.status, data: await safeJson(res) }
        }
      }
    ]
  },
  {
    key: 'message',
    label: 'Message 消息',
    apis: [
      {
        key: 'message-list',
        name: '获取消息列表',
        method: 'GET',
        path: '/api/messages',
        desc: '获取用户消息列表',
        params: [],
        execute: async () => {
          const res = await fetch(`${baseUrl}/api/messages`, { headers: getHeaders() })
          return { status: res.status, data: await safeJson(res) }
        }
      },
      {
        key: 'message-delete',
        name: '删除消息',
        method: 'DELETE',
        path: '/api/messages',
        desc: '批量删除消息',
        params: [
          { key: 'messageIds', label: '消息ID', placeholder: 'id1,id2,id3', type: 'text' }
        ],
        execute: async (params) => {
          const ids = (params.messageIds || '').split(',').map(s => s.trim()).filter(Boolean)
          const res = await fetch(`${baseUrl}/api/messages`, {
            method: 'DELETE',
            headers: getHeaders(),
            body: JSON.stringify({ messageIds: ids })
          })
          return { status: res.status, data: await safeJson(res) }
        }
      }
    ]
  },
  {
    key: 'file',
    label: 'File 文件',
    apis: [
      {
        key: 'file-upload',
        name: '上传文件',
        method: 'POST',
        path: '/api/file/upload',
        desc: '上传文件到服务器',
        params: [
          { key: 'file', label: '文件', type: 'file' }
        ],
        execute: async (params) => {
          if (!params.file) throw new Error('请选择文件')
          const formData = new FormData()
          formData.append('file', params.file)
          const res = await fetch(`${baseUrl}/api/file/upload`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${userStore.token.value}` },
            body: formData
          })
          return { status: res.status, data: await safeJson(res) }
        }
      }
    ]
  },
  {
    key: 'speech',
    label: 'Speech 语音',
    apis: [
      {
        key: 'speech-transcribe',
        name: '语音转文字',
        method: 'POST',
        path: '/api/speech/bailian/transcribe',
        desc: '将音频文件转换为文字',
        params: [
          { key: 'file', label: '音频文件', type: 'file', accept: 'audio/*' }
        ],
        execute: async (params) => {
          if (!params.file) throw new Error('请选择音频文件')
          const formData = new FormData()
          formData.append('file', params.file)
          const res = await fetch(`${baseUrl}/api/speech/bailian/transcribe`, {
            method: 'POST',
            headers: { 'Authorization': `Bearer ${userStore.token.value}` },
            body: formData
          })
          return { status: res.status, data: await safeJson(res) }
        }
      }
    ]
  }
]

const getHeaders = () => ({
  'Content-Type': 'application/json',
  'Authorization': `Bearer ${userStore.token.value}`
})

const safeJson = async (res) => {
  const text = await res.text()
  try {
    return JSON.parse(text)
  } catch {
    return { raw: text }
  }
}

const selectApi = (api) => {
  selectedApi.value = api
  response.value = null
  responseStatus.value = ''
  Object.keys(paramValues).forEach(k => delete paramValues[k])
  api.params?.forEach(p => {
    if (p.type === 'number') {
      paramValues[p.key] = p.min || null
    } else {
      paramValues[p.key] = ''
    }
  })
}

const executeApi = async () => {
  if (!selectedApi.value) return
  
  loading.value = true
  response.value = null
  responseStatus.value = ''
  
  try {
    const result = await selectedApi.value.execute(paramValues)
    response.value = result.data
    responseStatus.value = result.status
    
    if (result.status >= 200 && result.status < 300) {
      responseColor.value = 'success'
      responseClass.value = 'success'
    } else if (result.status >= 400 && result.status < 500) {
      responseColor.value = 'warning'
      responseClass.value = 'warning'
    } else {
      responseColor.value = 'error'
      responseClass.value = 'error'
    }
  } catch (e) {
    response.value = { error: e.message }
    responseStatus.value = 'Error'
    responseColor.value = 'error'
    responseClass.value = 'error'
  } finally {
    loading.value = false
  }
}

const formattedResponse = computed(() => {
  if (response.value === null) return ''
  return JSON.stringify(response.value, null, 2)
})
</script>

<style scoped>
.test-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 24px;
  background: #fff;
  border-bottom: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.test-container {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.api-list {
  width: 260px;
  background: #fff;
  border-right: 1px solid #e8e8e8;
  overflow-y: auto;
  flex-shrink: 0;
}

.api-group {
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
}

.api-group:last-child {
  border-bottom: none;
}

.group-title {
  padding: 0 16px 8px;
  font-size: 12px;
  font-weight: 600;
  color: #8c8c8c;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.api-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  transition: all 0.2s;
}

.api-item:hover {
  background: #f5f5f5;
}

.api-item.active {
  background: #e6f7ff;
  border-right: 3px solid #1890ff;
}

.api-name {
  font-size: 13px;
  color: #333;
}

.api-item.active .api-name {
  color: #1890ff;
  font-weight: 500;
}

.api-detail {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: #fff;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.detail-path {
  font-family: 'SF Mono', 'Consolas', monospace;
  font-size: 16px;
  font-weight: 500;
  color: #1a1a1a;
}

.detail-desc {
  color: #666;
  font-size: 13px;
  margin-bottom: 24px;
}

.params-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #333;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.params-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
}

.param-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.param-item label {
  font-size: 12px;
  color: #666;
  font-weight: 500;
}

.file-input {
  padding: 8px 0;
  font-size: 13px;
}

.action-section {
  margin-bottom: 24px;
}

.response-section {
  background: #fafafa;
  border-radius: 8px;
  overflow: hidden;
}

.response-section .section-title {
  padding: 12px 16px;
  margin-bottom: 0;
  background: #f5f5f5;
  border-bottom: 1px solid #e8e8e8;
}

.response-body {
  padding: 16px;
  max-height: 400px;
  overflow-y: auto;
}

.response-body pre {
  margin: 0;
  font-size: 12px;
  font-family: 'SF Mono', 'Consolas', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}

.response-body.success {
  border-left: 3px solid #52c41a;
}

.response-body.warning {
  border-left: 3px solid #faad14;
}

.response-body.error {
  border-left: 3px solid #ff4d4f;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
  color: #bfbfbf;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.empty-state p {
  margin: 0;
  font-size: 14px;
}
</style>
