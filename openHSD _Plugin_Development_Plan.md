# openHSD 插件开发思路文档

## 项目概述

openHSD 是一个本地-云端连接层插件，用于建立 Web/App 端、云端后端、本地 OpenClaw 之间的消息枢纽。通过 WebSocket 长连接实现实时通信，支持跨域消息中转和异步处理。

---

## 核心架构

```
┌─────────────┐         ┌──────────────┐         ┌──────────────┐
│ Web/App 端  │◄────────┤  云端后端    │◄────────┤  本地 openHSD   │
│   (UI)      │ HTTP API│  (消息总线)  │WebSocket│   Plugin     │
└─────────────┘         └──────────────┘         └──────────────┘
                               ▲                         │
                               │                         ▼
                               └─────────────────────────┤
                                  (本地 HTTP SSE)
                                 ┌──────────────┐
                                 │   OpenClaw   │
                                 │   (执行层)   │
                                 └──────────────┘
```

### 通信流程

1. **Web 端 → 云端**：用户通过 Web/App 界面发送消息
   - HTTP POST 请求到云端后端 REST API（携带 JWT）
   - 后端验证身份、记录消息、返回 message ID
   - 若 openHSD 离线，立即返回 `{ status: 'claw_offline' }`

2. **云端 → openHSD**：后端通过 WebSocket 精准路由消息
   - 从 JWT 中取出 userId，查找该用户对应的 openHSD WS session
   - 直接推送消息数据包（精准路由，非广播）
   - 包含：message ID、content、user context、timeout 等

3. **openHSD → OpenClaw**：本地插件通过 HTTP SSE 调用 OpenClaw
   - `POST http://localhost:8000/api/execute`，响应为 `text/event-stream`
   - 传递消息内容、sessionKey、上下文参数
   - 支持通过 AbortController 取消执行（超时/用户中断）

4. **OpenClaw 执行**：OpenClaw 处理请求
   - 调用相应的工具、技能、sub-agent
   - 通过 SSE 流式返回结果（边执行边输出）

5. **openHSD → 云端**：插件将结果实时回传
   - 通过 WebSocket 发送流式 chunk 或完整响应
   - 支持 `response_chunk`（流式）和 `response`（终态）两种格式

6. **云端 → Web 端**：后端推送结果给 Web 客户端
   - 通过独立的 Web WS 连接（`/ws/web/{userId}`）推送
   - 用户界面实时更新显示结果

---

## 技术方案

### 1. openHSD Plugin（本地独立进程）

**语言/框架：** Node.js（独立进程，与 OpenClaw 分开部署）

**进程内部模块结构：**

```
openHSD (独立 Node.js 进程)
├── WsClient          # 与云端 WSS 的长连接管理
├── MessageDispatcher # 并发任务调度（Map 管理 in-flight 任务）
├── ClawGateway       # 调用 OpenClaw HTTP SSE 接口
└── ConfigLoader      # 读取 cj.config.json
```

**主要模块：**

- **WebSocket 客户端（WsClient）**
  - 与云端后端长连接，连接时携带 token 完成身份认证
  - 自动重连机制（指数退避：1s → 2s → 4s → ... 最大 60s）
  - 心跳包保活（默认 30s 发一次 ping）
  - 重连成功后主动发送 `sync` 请求，补偿断线期间积压消息

- **任务调度器（MessageDispatcher）**
  - 用 `Map<messageId, AbortController>` 管理所有并发任务
  - 每条消息独立 AbortController，支持超时自动取消
  - 超时后发送 `{ status: 'timeout' }` 回传云端

  ```js
  const inflight = new Map(); // messageId -> AbortController

  wsClient.on('message', async (msg) => {
    const { messageId, content, timeout } = JSON.parse(msg);

    const controller = new AbortController();
    inflight.set(messageId, controller);
    const timer = setTimeout(() => controller.abort(), timeout);

    try {
      await clawGateway.executeStream(content, {
        signal: controller.signal,
        onChunk: (chunk) => wsClient.send({ type: 'response_chunk', messageId, chunk }),
        onDone: ()  => wsClient.send({ type: 'response', messageId, status: 'completed' }),
      });
    } catch (e) {
      const status = e.name === 'AbortError' ? 'timeout' : 'error';
      wsClient.send({ type: 'response', messageId, status });
    } finally {
      clearTimeout(timer);
      inflight.delete(messageId);
    }
  });
  ```

- **OpenClaw 网关（ClawGateway）**
  - 通过 HTTP SSE 调用 OpenClaw：`POST /api/execute`，响应 `text/event-stream`
  - 使用 `fetch` + `AbortSignal` 实现可取消的流式读取
  - 直接 pipe SSE 数据行 → 转为 WS chunk 发往云端，无大内存缓冲

  ```js
  async executeStream(content, { signal, onChunk, onDone }) {
    const res = await fetch('http://localhost:8000/api/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: content }),
      signal, // 绑定 AbortController，超时自动断流
    });

    for await (const line of res.body) {
      if (line.startsWith('data:')) {
        onChunk(line.slice(5).trim());
      }
    }
    onDone();
  }
  ```

- **重连补偿机制**
  - openHSD 断线期间，云端积压的消息状态保持 `pending`
  - 重连成功后主动发送同步请求，云端批量重推积压消息

  ```js
  ws.on('open', () => {
    retryCount = 0;
    startHeartbeat();
    // 重连后补偿积压消息
    ws.send(JSON.stringify({ type: 'sync', clawId: this.clawId }));
  });
  ```

- **配置管理**
  - OpenClaw 本地地址/端口
  - 云端 WebSocket 地址
  - 用户从 Web 端复制的认证 token（用于 WS 握手鉴权）
  - 超时策略

**配置文件示例 (cj.config.json)：**

```json
{
  "openclaw": {
    "baseUrl": "http://localhost:8000",
    "apiPath": "/api/v1"
  },
  "cloud": {
    "wsUrl": "wss://your-backend.com/ws/claw",
    "token": "cj_token_xxx",
    "heartbeatInterval": 30000
  },
  "plugin": {
    "requestTimeout": 30000,
    "maxQueueSize": 1000
  }
}
```

> **token 获取方式：** 用户登录 Web 端后，在设置页生成专属 API Token，手动复制粘贴到此配置文件。

---

### 2. 云端后端改版（Spring Boot）

**两类 WebSocket 连接明确区分：**

| 连接类型 | 客户端 | 路径 | 用途 |
|---|---|---|---|
| Claw WS | openHSD Plugin | `/ws/claw` | 下发任务、接收执行结果 |
| Web WS | 浏览器 | `/ws/web/{userId}` | 向用户推送结果 |

**需要新增/修改的接口：**

- **openHSD 连接端点** `wss://your-backend.com/ws/claw`
  - 握手时验证 token，解析出 userId，注册到 `clawSessions[userId]`
  - 接收 openHSD 上报的执行结果，更新数据库并转发给 Web WS

- **Web 客户端连接端点** `wss://your-backend.com/ws/web/{userId}`
  - 用于向浏览器推送流式结果和状态更新

- **消息入队 API** `POST /api/message`
  - Web 端发送消息（携带 JWT）
  - 验证身份，记录消息，status=pending
  - 查找 `clawSessions[userId]`：找到则推送并改 status=processing，找不到则返回 `claw_offline`

- **会话管理**
  - 追踪消息状态（pending | processing | completed | failed | timeout）
  - 处理 openHSD 断线时的超时清理

**Spring Boot 关键实现：**

```java
// WebSocket 配置，区分两类连接
@Configuration
@EnableWebSocket
public class WsConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(clawWsHandler(), "/ws/claw")
                .addInterceptors(new ClawHandshakeInterceptor()) // token 鉴权
                .setAllowedOrigins("*");
        registry.addHandler(webWsHandler(), "/ws/web/{userId}")
                .addInterceptors(new WebHandshakeInterceptor())  // JWT 鉴权
                .setAllowedOrigins("*");
    }
}

// 握手拦截器：验证 token，解析 userId
public class ClawHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest req, ServerHttpResponse res,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        String token = extractToken(req); // 从 query param 或 Authorization header
        Long userId = tokenService.validate(token);
        if (userId == null) return false; // 拒绝连接
        attributes.put("userId", userId);
        return true;
    }
}

// 连接注册表（内存，单节点）
ConcurrentHashMap<Long, WebSocketSession> clawSessions = new ConcurrentHashMap<>();
ConcurrentHashMap<Long, WebSocketSession> webSessions  = new ConcurrentHashMap<>();
```

**精准路由逻辑：**

```java
// 收到 Web 端消息后的路由
public void dispatchMessage(Long userId, String messageId, String content) {
    WebSocketSession clawSession = clawSessions.get(userId);
    if (clawSession == null || !clawSession.isOpen()) {
        // openHSD 离线，直接告知前端
        updateMessageStatus(messageId, "claw_offline");
        pushToWeb(userId, buildOfflineResponse(messageId));
        return;
    }
    // 精准推送给该用户的 openHSD
    updateMessageStatus(messageId, "processing");
    clawSession.sendMessage(new TextMessage(buildRequest(messageId, content)));
}
```

**断线补偿（处理 sync 请求）：**

```java
// openHSD 重连后发来 sync，批量重推 pending 消息
case "sync": {
    Long userId = (Long) session.getAttributes().get("userId");
    List<Message> pending = messageRepo.findByUserIdAndStatus(userId, "pending");
    for (Message msg : pending) {
        session.sendMessage(new TextMessage(buildRequest(msg.getId(), msg.getContent())));
    }
}
```

**数据库表建议：**

```
messages
├── id (PK)
├── user_id
├── content
├── status (pending | processing | completed | failed | timeout | claw_offline)
├── request_time
├── openclaw_request_id (关联 OpenClaw 执行 ID)
├── result
└── completed_time

cj_sessions
├── id (PK)
├── user_id         (关联用户，由 token 解析)
├── connected_at
├── last_heartbeat
└── status (connected | disconnected)
```

---

### 3. Web 端改版

**需要新增的交互：**

- **消息发送**
  - 用户输入 → 点击发送
  - 前端生成 optimistic message（本地先显示）
  - POST 到后端，获取 message ID
  - 订阅该消息的结果更新（通过已建立的 Web WS 连接）

- **实时更新**
  - 建立 WebSocket 连接到 `/ws/web/{userId}`
  - 接收流式 chunk（`response_chunk`）逐步追加显示
  - 接收终态（`response`）更新最终状态

- **状态展示**
  - 消息状态指示器：待发送 → 发送中 → 执行中 → 完成 / 失败
  - openHSD 离线提示（`claw_offline`）+ 引导用户启动 openHSD
  - 超时提示 + 错误提示 + 重试按钮

---

## 消息格式定义

### openHSD ← 云端（下发任务）

```json
{
  "type": "request",
  "messageId": "msg_12345",
  "userId": "user_001",
  "content": "请帮我总结一下今天的日程",
  "context": {
    "timezone": "Asia/Shanghai",
    "sessionKey": "session_xxx"
  },
  "timeout": 30000
}
```

### openHSD → OpenClaw（HTTP SSE 请求体）

```json
{
  "message": "请帮我总结一下今天的日程",
  "sessionKey": "session_xxx",
  "timeout": 30000
}
```

### openHSD → 云端（流式 chunk）

```json
{
  "type": "response_chunk",
  "messageId": "msg_12345",
  "chunk": "今天的日程包括...",
  "isLast": false
}
```

### openHSD → 云端（终态响应）

```json
{
  "type": "response",
  "messageId": "msg_12345",
  "status": "completed",
  "timestamp": 1704067200000,
  "executionTime": 5432
}
```

> `status` 可选值：`completed` | `error` | `timeout`

### openHSD → 云端（重连同步）

```json
{
  "type": "sync",
  "clawId": "claw_abc123"
}
```

---

## 部署架构

```
┌────────────────────────────────────────┐
│          用户终端 (Web/App)             │
└────────────────────────────────────────┘
                    │ HTTP + WSS(/ws/web/{userId})
                    ▼
┌────────────────────────────────────────┐
│    云端后端 (Spring Boot)               │
│  ┌──────────────────────────────────┐  │
│  │  REST API                        │  │
│  │  WS Server: /ws/claw             │  │
│  │  WS Server: /ws/web/{userId}     │  │
│  │  数据库 (MySQL)                  │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
                    │ WSS(/ws/claw) + token 鉴权
                    ▼
┌────────────────────────────────────────┐
│     用户本机 (Windows)                  │
│  ┌──────────────────────────────────┐  │
│  │  openHSD Plugin（独立 Node 进程）  │  │
│  │  WsClient + MessageDispatcher    │  │
│  │  ClawGateway + ConfigLoader      │  │
│  └──────────────────────────────────┘  │
│                   │ HTTP SSE            │
│                   ▼                    │
│  ┌──────────────────────────────────┐  │
│  │       OpenClaw Agent             │  │
│  │  (执行工具、发送消息、调用 API)   │  │
│  └──────────────────────────────────┘  │
└────────────────────────────────────────┘
```
