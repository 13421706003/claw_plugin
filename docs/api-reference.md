# openHSD 后端 API 接口文档

## 目录

- [认证模块](#认证模块)
- [消息模块](#消息模块)
- [设备模块](#设备模块)
- [文件模块](#文件模块)
- [推送模块](#推送模块)
- [充值模块](#充值模块)
- [语音模块](#语音模块)

---

## 认证模块

### POST `/api/auth/login`

用户登录，验证用户名和密码，成功后返回 JWT Token。

**请求体**

```json
{
  "username": "admin",
  "password": "123456"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名 |
| password | string | 是 | 密码 |

**响应**

成功：
```json
{
  "success": true,
  "message": "登录成功",
  "token": "eyJhbGciOiJIUzI1NiIs...",
  "userId": 1,
  "username": "admin"
}
```

失败：
```json
{
  "success": false,
  "message": "用户名或密码错误"
}
```

---

### POST `/api/auth/register`

用户注册，创建新用户账号。

**请求体**

```json
{
  "username": "newuser",
  "password": "123456"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| username | string | 是 | 用户名（唯一） |
| password | string | 是 | 密码（至少6位） |

**响应**

成功：
```json
{
  "success": true,
  "message": "注册成功"
}
```

失败：
```json
{
  "success": false,
  "message": "用户名已存在"
}
```

---

## 消息模块

### GET `/api/messages`

获取历史消息列表。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | long | 是 | 用户ID |
| clawId | string | 是 | 设备ID |

**示例**

```
GET /api/messages?userId=1&clawId=claw_abc123
```

**响应**

```json
{
  "success": true,
  "userId": 1,
  "clawId": "claw_abc123",
  "count": 10,
  "messages": [
    {
      "messageId": "msg_001",
      "role": "user",
      "content": "你好",
      "attachments": null,
      "createdAt": "2024-01-01T12:00:00"
    },
    {
      "messageId": "msg_002",
      "role": "assistant",
      "content": "你好，有什么可以帮助你的？",
      "attachments": null,
      "createdAt": "2024-01-01T12:00:05"
    }
  ]
}
```

---

### DELETE `/api/messages`

清空历史消息。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | long | 是 | 用户ID |
| clawId | string | 是 | 设备ID |

**示例**

```
DELETE /api/messages?userId=1&clawId=claw_abc123
```

**响应**

```json
{
  "success": true,
  "message": "历史消息已清空"
}
```

---

## 设备模块

### GET `/api/claw/status`

查询设备在线状态。

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 是 | 用户ID |

**示例**

```
GET /api/claw/status?userId=1
```

**响应**

```json
{
  "userId": "1",
  "online": true,
  "clawCount": 2,
  "clawList": [
    {
      "clawId": "claw_abc123",
      "openClawDeviceId": "device_001",
      "connectedAt": 1704067200000
    },
    {
      "clawId": "claw_def456",
      "openClawDeviceId": "device_002",
      "connectedAt": 1704067260000
    }
  ],
  "totalOnline": 5
}
```

---

### POST `/api/claw/send`

向指定设备发送消息。

**请求体**

```json
{
  "userId": "1",
  "messageId": "msg_1704067200000_1",
  "content": "请帮我分析这个文件",
  "clawId": "claw_abc123",
  "tabId": "tab_001",
  "timeout": 60000,
  "context": {
    "sessionKey": "main",
    "thinking": true
  },
  "attachments": [
    {
      "objectKey": "uploads/1/claw_abc123/msg_001/0.pdf",
      "name": "document.pdf",
      "type": "application/pdf",
      "size": 102400
    }
  ]
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 是 | 用户ID |
| messageId | string | 是 | 消息ID（前端生成） |
| content | string | 是 | 消息内容 |
| clawId | string | 是 | 目标设备ID |
| tabId | string | 否 | 标签页ID（用于路由响应） |
| timeout | integer | 否 | 超时时间（毫秒） |
| context | object | 否 | 上下文信息 |
| attachments | array | 否 | 附件列表 |

**响应**

成功：
```json
{
  "success": true,
  "userId": "1",
  "messageId": "msg_1704067200000_1",
  "targetClawId": "claw_abc123",
  "message": "已下发给插件 claw_abc123"
}
```

失败：
```json
{
  "success": false,
  "message": "指定的机器 claw_abc123 不在线"
}
```

---

### POST `/api/claw/broadcast`

向用户所有在线设备广播消息。

**请求体**

```json
{
  "userId": "1",
  "messageId": "msg_1704067200000_1",
  "content": "广播消息内容",
  "tabId": "tab_001",
  "attachments": []
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 是 | 用户ID |
| messageId | string | 是 | 消息ID（前端生成） |
| content | string | 是 | 消息内容 |
| tabId | string | 否 | 标签页ID |
| attachments | array | 否 | 附件列表 |

**响应**

```json
{
  "success": true,
  "userId": "1",
  "messageId": "msg_1704067200000_1",
  "sentClawIds": ["claw_abc123", "claw_def456"],
  "sentMessageIds": ["msg_1704067200000_1_claw_abc123", "msg_1704067200000_1_claw_def456"],
  "failedClawIds": [],
  "message": "已广播给 2 台设备"
}
```

---

### POST `/api/claw/abort`

中断正在运行的 AI 对话任务。

**请求体**

```json
{
  "userId": "1",
  "messageId": "msg_1704067200000_1",
  "clawId": "claw_abc123"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 是 | 用户ID |
| messageId | string | 是 | 要中断的消息ID |
| clawId | string | 否 | 设备ID，不传则查找该用户所有设备 |

**响应**

成功：
```json
{
  "success": true,
  "messageId": "msg_1704067200000_1",
  "runId": "run_xyz789",
  "message": "已发送中断指令"
}
```

失败：
```json
{
  "success": false,
  "message": "未找到对应的运行任务，可能已完成或不存在"
}
```

---

## 文件模块

### POST `/api/file/upload`

上传文件到 MinIO 对象存储。

**请求**

Content-Type: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| files | file[] | 是 | 文件列表（支持多文件） |
| userId | string | 是 | 用户ID |
| clawId | string | 是 | 设备ID |

**限制**

- 单文件最大 50MB
- 支持的文件类型：
  - 图片：`image/*`
  - 文档：`application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.*`
  - 文本：`text/plain`, `text/markdown`, `text/csv`
  - 压缩：`application/zip`, `application/gzip`
  - 数据：`application/json`

**响应**

```json
{
  "success": true,
  "files": [
    {
      "objectKey": "uploads/1/claw_abc123/upload/0.pdf",
      "url": "https://minio.example.com/bucket/uploads/...",
      "name": "document.pdf",
      "type": "application/pdf",
      "size": 102400
    }
  ]
}
```

---

## 推送模块

### POST `/api/push/file`

推送文件通知给前端（插件调用）。

**请求体**

```json
{
  "userId": "1",
  "clawId": "claw_abc123",
  "fileUrl": "https://example.com/file.pdf",
  "fileName": "report.pdf",
  "fileType": "application/pdf",
  "fileSize": 102400
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | string | 是 | 用户ID |
| clawId | string | 是 | 设备ID |
| fileUrl | string | 是 | 文件URL |
| fileName | string | 是 | 文件名 |
| fileType | string | 是 | MIME类型 |
| fileSize | long | 是 | 文件大小（字节） |

**响应**

```json
{
  "success": true,
  "userId": "1",
  "clawId": "claw_abc123",
  "messageId": "file_push_1704067200000",
  "message": "已推送给用户"
}
```

---

## 充值模块

### GET `/api/recharge/key-info`

获取用户绑定的 API Key 信息。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**响应**

```json
{
  "success": true,
  "bound": true,
  "keyInfo": {
    "keyHash": "sk-or-...",
    "limit": 100.00,
    "usage": 25.50,
    "remaining": 74.50
  }
}
```

---

### POST `/api/recharge/bind-key`

绑定 OpenRouter API Key。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**请求体**

```json
{
  "apiKey": "sk-or-v1-..."
}
```

**响应**

```json
{
  "success": true,
  "message": "绑定成功"
}
```

---

### GET `/api/recharge/exchange-rate`

获取当前汇率。

**响应**

```json
{
  "success": true,
  "rate": 7.2
}
```

---

### POST `/api/recharge/create`

创建充值订单。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**请求体**

```json
{
  "amountUsd": 10.00,
  "paymentChannel": "wechat",
  "paymentType": "NATIVE"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| amountUsd | double | 是 | 充值金额（美元） |
| paymentChannel | string | 是 | 支付渠道：`wechat` / `alipay` |
| paymentType | string | 否 | 支付方式：`NATIVE`（扫码）/ `H5`（跳转） |

**响应**

```json
{
  "success": true,
  "orderNo": "ORD20240101120000",
  "amountUsd": 10.00,
  "amountCny": 72.00,
  "qrCodeUrl": "weixin://wxpay/...",
  "expireAt": 1704070800000
}
```

---

### GET `/api/recharge/status/{orderNo}`

查询订单状态。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**路径参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| orderNo | string | 是 | 商户订单号 |

**响应**

```json
{
  "success": true,
  "orderNo": "ORD20240101120000",
  "status": "PAID",
  "amountUsd": 10.00,
  "paidAt": "2024-01-01T12:05:00"
}
```

---

### GET `/api/recharge/history`

获取充值历史记录。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**请求参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| limit | int | 否 | 返回记录数量，默认10，最多50 |

**响应**

```json
{
  "success": true,
  "orders": [
    {
      "orderNo": "ORD20240101120000",
      "amountUsd": 10.00,
      "amountCny": 72.00,
      "status": "PAID",
      "createdAt": "2024-01-01T12:00:00",
      "paidAt": "2024-01-01T12:05:00"
    }
  ]
}
```

---

### POST `/api/recharge/query-status/{orderNo}`

主动查询并同步订单状态（支付完成后调用）。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**响应**

```json
{
  "success": true,
  "orderNo": "ORD20240101120000",
  "status": "PAID",
  "synced": true
}
```

---

### POST `/api/recharge/cancel/{orderNo}`

取消订单。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**响应**

```json
{
  "success": true,
  "message": "订单已取消"
}
```

---

### POST `/api/recharge/mock/pay/{orderNo}`

模拟支付成功（仅模拟模式可用）。

**请求头**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| Authorization | string | 是 | Bearer Token |

**响应**

```json
{
  "success": true,
  "message": "模拟支付成功"
}
```

---

### POST `/api/recharge/wechat/notify`

微信支付回调（由微信服务器调用）。

---

### POST `/api/recharge/alipay/notify`

支付宝支付回调（由支付宝服务器调用）。

---

## 语音模块

### POST `/api/speech/bailian/transcribe`

语音识别，上传音频文件返回识别文本。

**请求**

Content-Type: `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| file | file | 是 | 音频文件（wav/mp3/m4a/aac等） |
| durationMs | long | 否 | 音频时长（毫秒），可选 |

**响应**

成功：
```json
{
  "success": true,
  "text": "你好，这是一段语音识别的文字结果",
  "done": true,
  "failed": false
}
```

失败：
```json
{
  "success": false,
  "done": true,
  "failed": true,
  "message": "识别失败：音频格式不支持"
}
```

---

## 通用响应格式

所有接口响应均为 JSON 格式，包含以下通用字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| success | boolean | 请求是否成功 |
| message | string | 提示信息（可选） |

**错误响应示例**

```json
{
  "success": false,
  "message": "错误描述"
}
```

---

## 认证说明

需要认证的接口需在请求头携带 JWT Token：

```
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

未携带 Token 或 Token 无效时返回：

```json
{
  "success": false,
  "message": "未登录"
}
```

HTTP 状态码：`401 Unauthorized`
