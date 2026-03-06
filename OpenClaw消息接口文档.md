# OpenClaw 消息接口文档

## 一、连接方式

**WebSocket 地址**：`ws://localhost:18789`

你的插件作为客户端，主动连接 OpenClaw：

```js
const ws = new WebSocket('ws://localhost:18789')
```

---

## 二、发送消息格式

### 请求结构

```json
{
  "type": "req",
  "id": "请求唯一ID",
  "method": "chat.send",
  "params": {
    "sessionKey": "main",
    "message": "用户消息内容",
    "idempotencyKey": "幂等键"
  }
}
```

### 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `type` | string | 是 | 固定值 `"req"` |
| `id` | string | 是 | 请求唯一标识，用于匹配响应 |
| `method` | string | 是 | 固定值 `"chat.send"` |
| `params.sessionKey` | string | 是 | 会话标识，默认 `"main"` |
| `params.message` | string | 是 | 发送给智能体的消息 |
| `params.idempotencyKey` | string | 是 | 幂等键，防止重复提交 |
| `params.thinking` | string | 否 | 思考模式：`low` / `medium` / `high` |
| `params.deliver` | boolean | 否 | 是否投递回外部渠道（飞书等） |
| `params.attachments` | array | 否 | 附件列表（图片等） |
| `params.timeoutMs` | number | 否 | 超时时间（毫秒） |

### 示例

```json
{
  "type": "req",
  "id": "req_001",
  "method": "chat.send",
  "params": {
    "sessionKey": "main",
    "message": "帮我生成一张猫的图片",
    "idempotencyKey": "key_001",
    "thinking": "medium"
  }
}
```

---

## 三、返回消息格式

OpenClaw 会返回两种类型的消息：**响应**和**事件**。

### 3.1 响应（立即返回）

发送消息后，OpenClaw 会立即返回一个响应，表示请求已接收：

```json
{
  "type": "res",
  "id": "req_001",
  "ok": true,
  "payload": {
    "runId": "run_abc123",
    "status": "started"
  }
}
```

**status 可能的值**：
- `started` - 新任务已启动
- `in_flight` - 任务正在运行（重复提交时）
- `ok` - 任务已完成

### 3.2 事件（流式回复）

智能体的回复通过 `chat` 事件推送，可能分多次推送（流式）：

```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "run_abc123",
    "sessionKey": "main",
    "seq": 0,
    "state": "delta",
    "message": {
      "role": "assistant",
      "content": "好的，我来帮你生成一张猫的图片..."
    }
  }
}
```

继续推送：

```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "run_abc123",
    "sessionKey": "main",
    "seq": 1,
    "state": "delta",
    "message": {
      "role": "assistant",
      "content": "正在调用图像生成模型..."
    }
  }
}
```

最终完成：

```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "run_abc123",
    "sessionKey": "main",
    "seq": 2,
    "state": "final",
    "message": {
      "role": "assistant",
      "content": "图片已生成完成！",
      "attachments": [
        {
          "type": "image",
          "url": "file:///path/to/image.png"
        }
      ]
    },
    "usage": {
      "inputTokens": 100,
      "outputTokens": 50
    },
    "stopReason": "end_turn"
  }
}
```

### 3.3 state 状态说明

| state | 说明 |
|-------|------|
| `delta` | 增量更新，流式输出中 |
| `final` | 最终完成，这是最后一条消息 |
| `aborted` | 任务被中断 |
| `error` | 出错 |

### 3.4 错误响应

```json
{
  "type": "event",
  "event": "chat",
  "payload": {
    "runId": "run_abc123",
    "sessionKey": "main",
    "state": "error",
    "errorMessage": "API 调用失败：超时"
  }
}
```

---

## 四、完整示例代码

### JavaScript 客户端

```js
const WebSocket = require('ws')

// 连接 OpenClaw
const ws = new WebSocket('ws://localhost:18789')

let requestId = 0

// 连接成功
ws.on('open', () => {
  console.log('已连接 OpenClaw')
})

// 接收消息
ws.on('message', (data) => {
  const msg = JSON.parse(data.toString())
  
  // 响应
  if (msg.type === 'res') {
    console.log('请求已接收:', msg.payload)
  }
  
  // 事件（流式回复）
  if (msg.type === 'event' && msg.event === 'chat') {
    const { state, message, errorMessage } = msg.payload
    
    if (state === 'delta') {
      // 流式输出，追加内容
      process.stdout.write(message.content || '')
    }
    
    if (state === 'final') {
      console.log('\n完成！')
      console.log('完整回复:', message.content)
      if (message.attachments) {
        console.log('附件:', message.attachments)
      }
    }
    
    if (state === 'error') {
      console.error('错误:', errorMessage)
    }
  }
})

// 发送消息函数
function sendMessage(text, sessionKey = 'main') {
  const id = `req_${++requestId}`
  const idempotencyKey = `key_${Date.now()}_${requestId}`
  
  const request = {
    type: 'req',
    id: id,
    method: 'chat.send',
    params: {
      sessionKey: sessionKey,
      message: text,
      idempotencyKey: idempotencyKey
    }
  }
  
  ws.send(JSON.stringify(request))
  return id
}

// 使用示例
setTimeout(() => {
  sendMessage('你好，请介绍一下你自己')
}, 1000)
```

### 转发消息示例（你的插件场景）

```js
const WebSocket = require('ws')

// 连接 OpenClaw
const openclaw = new WebSocket('ws://localhost:18789')

let requestId = 0
let pendingRequests = new Map()

openclaw.on('message', (data) => {
  const msg = JSON.parse(data.toString())
  
  // 处理流式回复
  if (msg.type === 'event' && msg.event === 'chat') {
    const { runId, state, message, errorMessage } = msg.payload
    
    // 根据 runId 找到对应的回调
    const callback = pendingRequests.get(runId)
    
    if (state === 'delta' && callback) {
      callback.onDelta(message.content)
    }
    
    if (state === 'final' && callback) {
      callback.onComplete(message)
      pendingRequests.delete(runId)
    }
    
    if (state === 'error' && callback) {
      callback.onError(errorMessage)
      pendingRequests.delete(runId)
    }
  }
  
  // 记录 runId
  if (msg.type === 'res' && msg.ok) {
    const { runId } = msg.payload
    // 可以在这里关联 requestId 和 runId
  }
})

// 发送消息并注册回调
function sendToOpenClaw(text, callbacks) {
  const id = `req_${++requestId}`
  const idempotencyKey = `key_${Date.now()}_${requestId}`
  
  const request = {
    type: 'req',
    id: id,
    method: 'chat.send',
    params: {
      sessionKey: 'main',
      message: text,
      idempotencyKey: idempotencyKey
    }
  }
  
  openclaw.send(JSON.stringify(request))
  
  // 临时存储回调（实际需要根据 runId 关联）
  pendingRequests.set(id, callbacks)
  
  return id
}

// 使用示例：收到后端消息后转发
backendSocket.on('message', (data) => {
  const { text, requestId } = JSON.parse(data)
  
  sendToOpenClaw(text, {
    onDelta: (content) => {
      // 流式转发回后端
      backendSocket.send(JSON.stringify({
        requestId,
        type: 'delta',
        content
      }))
    },
    onComplete: (message) => {
      // 完成时转发
      backendSocket.send(JSON.stringify({
        requestId,
        type: 'complete',
        message
      }))
    },
    onError: (error) => {
      backendSocket.send(JSON.stringify({
        requestId,
        type: 'error',
        error
      }))
    }
  })
})
```

---

## 五、常见问题

### Q1: idempotencyKey 有什么用？

用于防止重复提交。如果相同的 `idempotencyKey` 重复发送，OpenClaw 会返回当前任务状态而不是重新执行。

### Q2: 如何区分不同的会话？

使用不同的 `sessionKey`：
- `main` - 默认主会话
- `agent:main:telegram:user:123` - Telegram 用户 123 的会话
- 自定义格式：`agent:<agentId>:<channel>:<target>`

### Q3: 如何发送带图片的消息？

```json
{
  "type": "req",
  "id": "req_001",
  "method": "chat.send",
  "params": {
    "sessionKey": "main",
    "message": "这张图片是什么？",
    "idempotencyKey": "key_001",
    "attachments": [
      {
        "type": "image",
        "url": "file:///path/to/image.png"
      }
    ]
  }
}
```

### Q4: 连接断开怎么办？

实现重连机制：

```js
function connect() {
  const ws = new WebSocket('ws://localhost:18789')
  
  ws.on('close', () => {
    console.log('连接断开，5秒后重连...')
    setTimeout(connect, 5000)
  })
  
  ws.on('error', (err) => {
    console.error('连接错误:', err)
  })
  
  return ws
}

const ws = connect()
```

---

## 六、消息流程图

```
你的插件                              OpenClaw
   │                                    │
   │──── 1. 发送请求 ─────────────────→│
   │     {type:"req", method:"chat.send"}│
   │                                    │
   │←─── 2. 立即响应 ────────────────────│
   │     {type:"res", status:"started"} │
   │                                    │
   │←─── 3. 流式事件 ────────────────────│
   │     {type:"event", state:"delta"}  │
   │                                    │
   │←─── 4. 流式事件 ────────────────────│
   │     {type:"event", state:"delta"}  │
   │                                    │
   │←─── 5. 最终事件 ────────────────────│
   │     {type:"event", state:"final"}  │
   │                                    │
```
