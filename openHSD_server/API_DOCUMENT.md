# OpenHSD Server API 接口文档

## 目录

- [概述](#概述)
- [认证说明](#认证说明)
- [通用响应格式](#通用响应格式)
- [接口列表](#接口列表)
  - [1. 认证模块 (Auth)](#1-认证模块-auth)
  - [2. 设备模块 (Claw)](#2-设备模块-claw)
  - [3. 文件模块 (File)](#3-文件模块-file)
  - [4. 消息模块 (Messages)](#4-消息模块-messages)
  - [5. 推送模块 (Push)](#5-推送模块-push)
- [MIME 类型参考](#mime-类型参考)
- [错误码说明](#错误码说明)

---

## 概述

| 项目 | 说明 |
|-----|------|
| 基础URL | `http://{host}:{port}/api` |
| 协议 | HTTP/1.1 |
| 数据格式 | JSON |
| 字符编码 | UTF-8 |

---

## 认证说明

除登录、注册和发送文件接口外，其他接口需要在请求头中携带 Token：

```
Authorization: Bearer <token>
```

Token 通过登录接口获取。

---

## 通用响应格式

### 成功响应

```json
{
  "success": true,
  "message": "操作成功",
  ...其他数据字段
}
```

### 失败响应

```json
{
  "success": false,
  "message": "错误描述"
}
```

---

## 接口列表

---

## 1. 认证模块 (Auth)

### 1.1 用户登录

**请求**

```
POST /api/auth/login
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| username | String | 是 | 用户名 |
| password | String | 是 | 密码 |

**请求示例**

```json
{
  "username": "admin",
  "password": "123456"
}
```

**成功响应**

```json
{
  "success": true,
  "message": "登录成功",
  "userId": 1,
  "username": "admin",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**失败响应**

```json
{
  "success": false,
  "message": "用户名或密码错误"
}
```

---

### 1.2 用户注册

**请求**

```
POST /api/auth/register
Content-Type: application/json
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| username | String | 是 | 用户名（不能为空） |
| password | String | 是 | 密码（长度不能少于6位） |

**请求示例**

```json
{
  "username": "newuser",
  "password": "123456"
}
```

**成功响应**

```json
{
  "success": true,
  "message": "注册成功"
}
```

**失败响应**

```json
{
  "success": false,
  "message": "用户名已存在"
}
```

---

## 2. 设备模块 (Claw)

### 2.1 查询设备状态

**请求**

```
GET /api/claw/status?userId={userId}
Authorization: Bearer <token>
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| userId | String | 是 | 用户ID（Query参数） |

**请求示例**

```
GET /api/claw/status?userId=1
```

**成功响应**

```json
{
  "userId": "1",
  "online": true,
  "clawCount": 2,
  "clawList": [
    {
      "clawId": "claw-001",
      "openClawDeviceId": "device-xxx",
      "lastHeartbeat": 1710000000000
    },
    {
      "clawId": "claw-002",
      "openClawDeviceId": "device-yyy",
      "lastHeartbeat": 1710000001000
    }
  ],
  "totalOnline": 15
}
```

**字段说明**

| 字段 | 类型 | 说明 |
|-----|------|------|
| online | Boolean | 用户是否有在线设备 |
| clawCount | Number | 在线设备数量 |
| clawList | Array | 在线设备列表 |
| clawList[].clawId | String | 设备唯一标识 |
| clawList[].openClawDeviceId | String | OpenClaw 设备 ID |
| clawList[].lastHeartbeat | Number | 最后心跳时间戳（毫秒） |
| totalOnline | Number | 系统总在线设备数 |

---

### 2.2 单设备发送消息

**请求**

```
POST /api/claw/send
Content-Type: application/json
Authorization: Bearer <token>
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| userId | String | 是 | 用户ID |
| messageId | String | 是 | 消息唯一标识（前端生成） |
| clawId | String | 是 | 目标设备ID |
| content | String | 否 | 消息文本内容 |
| attachments | Array | 否 | 附件列表 |
| context | Object | 否 | 上下文信息 |
| timeout | Number | 否 | 超时时间（毫秒） |

**attachments 字段说明**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| objectKey | String | 二选一 | MinIO 对象键（文件已预上传时使用，经过特殊处理，在插件部分对文件拉取下来做了文本获取处理） |
| base64 | String | 二选一 | 附件图片发送给openclaw只能通过Base64 编码数据传输（图片粘贴时使用） |
| name | String | 否 | 文件名 |
| type | String | 否 | MIME 类型 |
| size | Number | 否 | 文件大小（字节） |

**请求示例（纯文本）**

```json
{
  "userId": "1",
  "messageId": "msg_1710000000001",
  "clawId": "claw-001",
  "content": "你好，请帮我分析这个文档"
}
```

**请求示例（带附件-预上传文件）**

```json
{
  "userId": "1",
  "messageId": "msg_1710000000002",
  "clawId": "claw-001",
  "content": "请分析这个PDF文件",
  "attachments": [
    {
      "objectKey": "hsdclaw/1/claw-001/upload/1710000000002_0.pdf",
      "name": "report.pdf",
      "type": "application/pdf",
      "size": 102400
    }
  ]
}
```

**请求示例（带附件-Base64图片）**

```json
{
  "userId": "1",
  "messageId": "msg_1710000000003",
  "clawId": "claw-001",
  "content": "这张图片是什么？",
  "attachments": [
    {
      "base64": "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAA...",
      "name": "screenshot.png",
      "type": "image/png",
      "size": 50000
    }
  ]
}
```

**成功响应**

```json
{
  "success": true,
  "userId": "1",
  "messageId": "msg_1710000000001",
  "targetClawId": "claw-001",
  "message": "已下发给插件 claw-001"
}
```

**失败响应**

```json
{
  "success": false,
  "message": "指定的机器 claw-001 不在线"
}
```

---

### 2.3 广播发送消息

向用户所有在线设备并发发送消息，每台设备分配独立的子消息ID：`{messageId}_{clawId}`

**请求**

```
POST /api/claw/broadcast
Content-Type: application/json
Authorization: Bearer <token>
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| userId | String | 是 | 用户ID |
| messageId | String | 是 | 消息唯一标识（前端生成） |
| content | String | 否 | 消息文本内容 |
| attachments | Array | 否 | 附件列表（格式同单设备发送） |
| context | Object | 否 | 上下文信息 |
| timeout | Number | 否 | 超时时间（毫秒） |

**请求示例**

```json
{
  "userId": "1",
  "messageId": "msg_1710000000004",
  "content": "请所有设备执行任务"
}
```

**成功响应**

```json
{
  "success": true,
  "userId": "1",
  "messageId": "msg_1710000000004",
  "sentClawIds": ["claw-001", "claw-002"],
  "sentMessageIds": ["msg_1710000000004_claw-001", "msg_1710000000004_claw-002"],
  "failedClawIds": [],
  "message": "已广播给 2 台设备"
}
```

**部分失败响应**

```json
{
  "success": true,
  "userId": "1",
  "messageId": "msg_1710000000004",
  "sentClawIds": ["claw-001"],
  "sentMessageIds": ["msg_1710000000004_claw-001"],
  "failedClawIds": ["claw-002"],
  "message": "已广播给 1 台设备，1 台失败"
}
```

**全部失败响应**

```json
{
  "success": false,
  "online": false,
  "message": "用户 1 无在线插件"
}
```

---

## 3. 文件模块 (File)

### 3.1 文件上传

**请求**

```
POST /api/file/upload
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| files | File[] | 是 | 文件列表（支持多文件） |
| userId | String | 是 | 用户ID |
| clawId | String | 是 | 设备ID（用于构造 MinIO 路径） |

**限制条件**

| 项目 | 限制 |
|-----|------|
| 单文件大小 | 最大 50MB |
| 支持的 MIME 类型 | 见 [MIME 类型参考](#mime-类型参考) |

**请求示例（curl）**

```bash
curl -X POST http://localhost:8080/api/file/upload \
  -H "Authorization: Bearer <token>" \
  -F "files=@/path/to/document.pdf" \
  -F "files=@/path/to/image.png" \
  -F "userId=1" \
  -F "clawId=claw-001"
```

**成功响应**

```json
{
  "success": true,
  "files": [
    {
      "objectKey": "hsdclaw/1/claw-001/upload/1710000000005_0.pdf",
      "url": "https://minio.example.com/bucket/hsdclaw/1/claw-001/upload/1710000000005_0.pdf?X-Amz-...",
      "name": "document.pdf",
      "type": "application/pdf",
      "size": 102400
    },
    {
      "objectKey": "hsdclaw/1/claw-001/upload/1710000000005_1.png",
      "url": "https://minio.example.com/bucket/hsdclaw/1/claw-001/upload/1710000000005_1.png?X-Amz-...",
      "name": "image.png",
      "type": "image/png",
      "size": 50000
    }
  ]
}
```

**失败响应（文件过大）**

```json
{
  "success": false,
  "message": "文件 [large_file.zip] 超过 50MB 限制"
}
```

**失败响应（不支持的类型）**

```json
{
  "success": false,
  "message": "不支持的文件类型：application/x-executable"
}
```

---

## 4. 消息模块 (Messages)

### 4.1 获取历史消息

**请求**

```
GET /api/messages?userId={userId}&clawId={clawId}
Authorization: Bearer <token>
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| userId | Long | 是 | 用户ID（Query参数） |
| clawId | String | 是 | 设备ID（Query参数） |

**请求示例**

```
GET /api/messages?userId=1&clawId=claw-001
```

**成功响应**

```json
{
  "success": true,
  "userId": 1,
  "clawId": "claw-001",
  "count": 5,
  "messages": [
    {
      "messageId": "msg_1710000000001",
      "role": "user",
      "content": "你好",
      "attachments": null,
      "createdAt": "2024-03-10T10:00:00"
    },
    {
      "messageId": "msg_1710000000002",
      "role": "assistant",
      "content": "你好！有什么我可以帮助你的吗？",
      "attachments": null,
      "createdAt": "2024-03-10T10:00:01"
    },
    {
      "messageId": "msg_1710000000003",
      "role": "user",
      "content": "",
      "attachments": "[{\"objectKey\":\"hsdclaw/1/claw-001/upload/xxx.pdf\",\"url\":\"https://...\",\"name\":\"report.pdf\",\"type\":\"application/pdf\",\"size\":102400}]",
      "createdAt": "2024-03-10T10:01:00"
    }
  ]
}
```

**消息字段说明**

| 字段 | 类型 | 说明 |
|-----|------|------|
| messageId | String | 消息唯一标识 |
| role | String | 角色：`user`（用户）/ `assistant`（助手） |
| content | String | 消息文本内容 |
| attachments | String | 附件JSON字符串（需解析） |
| createdAt | String | 创建时间（ISO 8601格式） |

**attachments 字段解析后结构**

```json
[
  {
    "objectKey": "hsdclaw/1/claw-001/upload/xxx.pdf",
    "url": "https://minio.example.com/bucket/hsdclaw/...?X-Amz-...",
    "name": "report.pdf",
    "type": "application/pdf",
    "size": 102400
  }
]
```

---

### 4.2 清空历史消息

**请求**

```
DELETE /api/messages?userId={userId}&clawId={clawId}
Authorization: Bearer <token>
```

**请求参数**

| 字段 | 类型 | 必填 | 说明 |
|-----|------|-----|------|
| userId | Long | 是 | 用户ID（Query参数） |
| clawId | String | 是 | 设备ID（Query参数） |

**请求示例**

```
DELETE /api/messages?userId=1&clawId=claw-001
```

**成功响应**

```json
{
  "success": true,
  "message": "历史消息已清空"
}
```

---

## 5. 推送模块 (Push)

### 5.1 文件推送

用于外部系统向在线用户推送文件，前端通过 WebSocket 接收推送消息。

**请求**

```
POST /api/push/file
Content-Type: application/json
Authorization: Bearer <token>
```

**请求参数**

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|-----|------|-----|-------|------|
| userId | String | 是 | - | 接收推送的用户ID |
| clawId | String | 是 | - | 目标设备ID |
| fileUrl | String | 是 | - | 文件可访问的URL地址 |
| fileName | String | 否 | `"file"` | 文件名（**必须包含扩展名**） |
| fileType | String | 否 | `"application/octet-stream"` | MIME类型 |
| fileSize | Number | 否 | `null` | 文件大小（字节） |

**fileName 要求**

- 必须包含文件扩展名，如 `report.pdf`、`document.docx`
- 不要包含路径，只需纯文件名
- 前端会根据扩展名判断文件类型

**fileType 推荐值**

| 文件类型 | MIME 类型 |
|---------|----------|
| PDF | `application/pdf` |
| Word (.docx) | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| Excel (.xlsx) | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| PowerPoint (.pptx) | `application/vnd.openxmlformats-officedocument.presentationml.presentation` |
| 图片 (JPEG) | `image/jpeg` |
| 图片 (PNG) | `image/png` |
| 文本 | `text/plain` |
| JSON | `application/json` |

**请求示例**

```json
{
  "userId": "1",
  "clawId": "claw-001",
  "fileUrl": "https://example.com/files/report.pdf",
  "fileName": "report.pdf",
  "fileType": "application/pdf",
  "fileSize": 102400
}
```

**成功响应（用户在线）**

```json
{
  "success": true,
  "userId": "1",
  "clawId": "claw-001",
  "messageId": "file_push_1710000000001",
  "message": "已推送给用户"
}
```

**失败响应（用户不在线）**

```json
{
  "success": false,
  "userId": "1",
  "clawId": "claw-001",
  "messageId": "file_push_1710000000001",
  "message": "用户未在线"
}
```

**失败响应（参数缺失）**

```json
{
  "success": false,
  "message": "缺少必填字段：userId / clawId / fileUrl"
}
```

**前端接收的 WebSocket 消息格式**

```json
{
  "type": "file_push",
  "messageId": "file_push_1710000000001",
  "clawId": "claw-001",
  "fileUrl": "https://example.com/files/report.pdf",
  "fileName": "report.pdf",
  "fileType": "application/pdf",
  "fileSize": 102400,
  "timestamp": 1710000000001
}
```

---

## MIME 类型参考

### 图片类型

| 扩展名 | MIME 类型 |
|-------|----------|
| .jpg, .jpeg | `image/jpeg` |
| .png | `image/png` |
| .gif | `image/gif` |
| .webp | `image/webp` |
| .bmp | `image/bmp` |
| .svg | `image/svg+xml` |

### 文档类型

| 扩展名 | MIME 类型 |
|-------|----------|
| .pdf | `application/pdf` |
| .doc | `application/msword` |
| .docx | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| .xls | `application/vnd.ms-excel` |
| .xlsx | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| .ppt | `application/vnd.ms-powerpoint` |
| .pptx | `application/vnd.openxmlformats-officedocument.presentationml.presentation` |
| .txt | `text/plain` |
| .md | `text/markdown` |
| .csv | `text/csv` |
| .json | `application/json` |

### 压缩类型

| 扩展名 | MIME 类型 |
|-------|----------|
| .zip | `application/zip` |
| .gz | `application/gzip` |

---

## 错误码说明

| HTTP 状态码 | 说明 |
|------------|------|
| 200 | 请求成功（业务成功或失败需查看响应体中的 success 字段） |
| 400 | 请求参数错误 |
| 401 | 未授权（未登录或 Token 无效） |
| 500 | 服务器内部错误 |

---

## 附录

### 前端文件类型判断逻辑

前端根据 `fileName` 和 `fileType` 判断文件是图片还是文档：

```javascript
// 优先级：MIME 类型 > 扩展名
// 只有当 MIME 不是文档类型时，才考虑扩展名

const imageExts = ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg']
const docExts = ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'txt', 'md', 'csv', 'json', 'zip', 'gz']

const ext = fileName.split('.').pop().toLowerCase()
const isImageByMime = fileType.startsWith('image/')
const isImageByExt = imageExts.includes(ext)
const isDocumentByExt = docExts.includes(ext)

// 判断逻辑
const isImage = isImageByMime || (isImageByExt && !isDocumentByExt)
```

### MinIO 对象键格式

```
hsdclaw/{userId}/{clawId}/{messageId}_{index}.{ext}
```

示例：
```
hsdclaw/1/claw-001/msg_1710000000001_0.pdf
```

---

*文档版本：1.0.0*
*更新时间：2024-03-10*
