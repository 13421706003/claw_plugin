# 思考消息功能集成测试文档

## 功能概述

本次更新添加了对模型思考消息的接收和显示功能，实现了思考内容与正式回复的并行独立流式传输。

## 修改文件清单

### 1. 插件层 (openHSD-plugin/src/service.mjs)

**修改内容：**
- 添加 `thinking` 事件监听（`stream: "thinking"`）
- 发送 `thinking_chunk` 消息给后端
- 在 `final` 消息中提取 `thinking` 内容并发送

**关键代码：**
```javascript
// 监听 thinking 事件流
if (msg.type === 'event' && msg.stream === 'thinking') {
  const { runId, data } = msg;
  const messageId = this.runMap.get(runId);
  const { text, delta } = data || {};
  
  if (delta) {
    this.wsClient.send({
      type: 'thinking_chunk',
      messageId,
      chunk: delta,
      fullText: text,
    });
  }
}

// 在 final 消息中提取 thinking 内容
if (state === 'final') {
  let thinkingContent = '';
  if (Array.isArray(message?.content)) {
    const thinkingBlock = message.content.find(c => c.type === 'thinking');
    if (thinkingBlock && thinkingBlock.thinking) {
      thinkingContent = thinkingBlock.thinking;
    }
  }
  
  this.wsClient.send({
    type: 'response',
    messageId,
    status: 'completed',
    result: message?.content ?? '',
    attachments: message?.attachments ?? [],
    thinking: thinkingContent,
  });
}
```

### 2. 后端层 (openHSD_server/src/main/java/com/hsd/websocket/ClawWebSocketHandler.java)

**修改内容：**
- 添加 `thinking_chunk` 消息类型处理
- 新增 `handleThinkingChunk()` 方法
- 修改 `handleResponse()` 方法，包含 `thinking` 字段

**关键代码：**
```java
// 消息路由
case "thinking_chunk" -> handleThinkingChunk(session, userId, json);

// 处理思考消息增量
private void handleThinkingChunk(WebSocketSession session, String userId, JSONObject json) {
    String messageId = json.getString("messageId");
    String chunk     = json.getString("chunk");
    String fullText  = json.getString("fullText");

    String payload = buildJson(
            "type",      "thinking_chunk",
            "messageId", messageId,
            "chunk",     chunk,
            "fullText",  fullText != null ? fullText : ""
    );

    String tabId = messageTabRouter.getTabId(messageId);
    if (tabId != null) {
        webSessionRegistry.pushToTab(userId, tabId, payload);
    } else {
        webSessionRegistry.pushToUser(userId, payload);
    }
}

// 在 final 消息中包含 thinking 字段
String payload = buildJson(
        "type",      "response",
        "messageId", messageId,
        "status",    status,
        "result",    result,
        "thinking",  thinking != null ? thinking : ""
);
```

### 3. 前端逻辑层 (openHSD/src/api/aiService.js)

**修改内容：**
- 添加 `pendingThinking` Map 缓冲区
- 处理 `thinking_chunk` 消息
- 新增 `updateThinkingMessageStreaming()` 方法
- 修改 `updateAssistantMessage()` 方法，包含 `thinking` 参数
- 消息对象添加 `thinking` 和 `thinkingStreaming` 字段

**关键代码：**
```javascript
// 思考内容缓冲区
const pendingThinking = new Map()

// 处理 thinking_chunk 消息
if (type === 'thinking_chunk') {
  const thinkingText = extractText(chunk) || chunk
  if (thinkingText) {
    const prev = pendingThinking.get(messageId) || ''
    const accumulated = prev + thinkingText
    pendingThinking.set(messageId, accumulated)
    updateThinkingMessageStreaming(messageId, accumulated)
  }
}

// 更新思考内容显示
const updateThinkingMessageStreaming = (messageId, thinking) => {
  const idx = messages.value.findIndex(m => m.messageId === messageId && m.role === 'assistant')
  if (idx >= 0) {
    messages.value[idx].thinking = thinking
    messages.value[idx].thinkingStreaming = true
  } else {
    messages.value.push({
      messageId,
      role: 'assistant',
      content: '',
      thinking,
      loading: true,
      streaming: false,
      thinkingStreaming: true
    })
  }
}
```

### 4. 前端渲染层 (openHSD/src/views/ChatView.vue)

**修改内容：**
- 在 `bubbleItems` 中添加思考内容标记
- 在 `messageRender` 中解析和渲染思考区域

**关键代码：**
```javascript
// 添加思考内容标记
if (msg.thinking && msg.role === 'assistant') {
  const thinkingMarker = `<!--thinking:${encodeURIComponent(msg.thinking)}|${msg.thinkingStreaming ? 'streaming' : 'completed'}-->`
  content = thinkingMarker + content
}

// 渲染思考区域
const thinkingRegex = /<!--thinking:([^|]*)\|([^>]*)-->/
const thinkingMatch = actualContent.match(thinkingRegex)
if (thinkingMatch) {
  actualContent = actualContent.replace(thinkingRegex, '')
  const thinkingText = decodeURIComponent(thinkingMatch[1])
  const thinkingStatus = thinkingMatch[2]
  
  if (thinkingText) {
    children.push(h('div', {
      style: {
        background: '#f0f5ff',
        border: '1px solid #d6e4ff',
        borderRadius: '8px',
        padding: '12px 16px',
        marginBottom: '12px',
        fontSize: '13px',
        lineHeight: '1.6',
        color: '#1d39c4',
      }
    }, [
      h('div', {
        style: {
          fontWeight: 600,
          marginBottom: '8px',
          display: 'flex',
          alignItems: 'center',
          gap: '6px',
        }
      }, [
        h('span', '💭 思考过程'),
        thinkingStatus === 'streaming' && h('span', {
          class: 'streaming-cursor',
          style: { fontSize: '12px', color: '#1677ff' }
        }, '▌')
      ]),
      h('div', {
        style: {
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }
      }, thinkingText)
    ]))
  }
}
```

## 消息流程图

```
OpenClaw (模型返回思考内容)
    ↓ stream: "thinking", data: { text, delta }
插件层 (service.mjs)
    ↓ 监听 thinking 事件
    ↓ 发送 thinking_chunk { messageId, chunk, fullText }
后端层 (ClawWebSocketHandler.java)
    ↓ handleThinkingChunk()
    ↓ 推送给前端
前端逻辑层 (aiService.js)
    ↓ 处理 thinking_chunk
    ↓ updateThinkingMessageStreaming()
    ↓ 更新 messages.value
前端渲染层 (ChatView.vue)
    ↓ bubbleItems 添加标记
    ↓ messageRender 渲染思考区域
用户界面
    ✓ 显示蓝色背景的思考区域
```

## 测试步骤

### 1. 准备工作

1. 确保后端服务已启动（Spring Boot）
2. 确保插件已启动（openHSD-plugin）
3. 确保前端已启动（Vue 3 开发服务器）

### 2. 测试场景

#### 场景 1：单设备思考消息测试

1. 打开前端页面，选择一台设备
2. 发送一条需要思考的消息（例如："请分析一下人工智能的发展趋势"）
3. 观察消息回复过程：
   - ✓ 应该先看到蓝色背景的"思考过程"区域
   - ✓ 思考内容应该流式更新（带闪烁光标）
   - ✓ 思考完成后，应该显示正式回复内容
   - ✓ 思考区域应该保留，可以回顾

#### 场景 2：广播模式思考消息测试

1. 选择"全部设备"模式
2. 发送一条消息
3. 观察多台设备的回复：
   - ✓ 每台设备应该独立显示思考过程
   - ✓ 思考内容和正式回复应该正确对应
   - ✓ 设备标识应该正确显示

#### 场景 3：思考消息中断测试

1. 发送一条消息
2. 在思考过程中点击"停止"按钮
3. 观察：
   - ✓ 思考应该停止
   - ✓ 正式回复应该显示"任务被中断"

#### 场景 4：历史消息加载测试

1. 发送带思考的消息
2. 刷新页面或切换设备
3. 切回原设备
4. 观察：
   - ✓ 历史消息应该正确加载
   - ✓ 思考内容应该正确显示

### 3. 验证点

- [ ] 思考内容是否正确显示
- [ ] 思考内容是否流式更新
- [ ] 思考区域样式是否正确（蓝色背景）
- [ ] 思考区域是否在正式回复上方
- [ ] 思考内容是否完整保存
- [ ] 历史消息是否正确加载思考内容
- [ ] 广播模式是否正常工作
- [ ] 中断功能是否正常

## 样式说明

思考区域的样式：
- 背景色：`#f0f5ff`（浅蓝色）
- 边框：`1px solid #d6e4ff`（蓝色边框）
- 文字颜色：`#1d39c4`（深蓝色）
- 标题：`💭 思考过程`
- 流式输出时：显示闪烁光标

## 数据结构

### 消息对象
```javascript
{
  messageId: 'msg_xxx',
  role: 'assistant',
  content: '正式回复内容',
  thinking: '思考内容',
  loading: false,
  streaming: false,
  thinkingStreaming: false
}
```

### WebSocket 消息格式

#### thinking_chunk
```json
{
  "type": "thinking_chunk",
  "messageId": "msg_xxx",
  "chunk": "思考内容增量...",
  "fullText": "完整思考内容..."
}
```

#### response (final)
```json
{
  "type": "response",
  "messageId": "msg_xxx",
  "status": "completed",
  "result": "正式回复内容",
  "thinking": "完整思考内容"
}
```

## 注意事项

1. **思考内容提取**：OpenClaw 返回的 `message.content` 数组中包含 `type: "thinking"` 的块
2. **流式传输**：思考内容和正式回复独立流式传输，互不干扰
3. **兼容性**：如果模型不支持思考功能，不会影响现有功能
4. **性能**：使用 Map 缓冲区减少 Vue 响应式更新开销

## 故障排查

### 问题 1：思考内容不显示

**可能原因：**
- OpenClaw 未返回思考事件
- 插件层未正确监听 `stream: "thinking"` 事件

**排查方法：**
1. 检查 OpenClaw 日志，确认是否发送 thinking 事件
2. 检查插件日志，确认是否收到 thinking 事件
3. 检查前端 WebSocket 连接是否正常

### 问题 2：思考内容显示不完整

**可能原因：**
- thinking_chunk 消息丢失
- 前端缓冲区未正确累积

**排查方法：**
1. 检查后端日志，确认 thinking_chunk 消息是否正常转发
2. 检查前端日志，确认 pendingThinking Map 是否正确更新

### 问题 3：思考区域样式异常

**可能原因：**
- CSS 样式冲突
- messageRender 渲染逻辑错误

**排查方法：**
1. 使用浏览器开发者工具检查样式
2. 检查 thinkingRegex 正则表达式是否正确匹配

## 更新日志

**版本：2026.4.10**
- ✅ 插件层添加 thinking 事件监听和转发
- ✅ 后端层添加 thinking_chunk 消息处理
- ✅ 前端逻辑层添加思考消息处理
- ✅ 前端渲染层添加思考区域显示
- ✅ 支持流式思考内容更新
- ✅ 支持历史消息加载思考内容

## 后续优化建议

1. **折叠功能**：添加思考区域的折叠/展开功能
2. **样式优化**：支持自定义思考区域样式
3. **性能优化**：长思考内容使用虚拟滚动
4. **导出功能**：支持导出思考内容
5. **搜索功能**：支持搜索思考内容
