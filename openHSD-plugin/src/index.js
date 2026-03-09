import { WsClient }   from './ws/WsClient.js';
import { ClawClient } from './ws/ClawClient.js';
import { setupToken } from './setup.js';

// ----------------------------------------------------------------
// 加载配置（先询问是否更新 token）
// ----------------------------------------------------------------
const config = await setupToken();

const defaultSessionKey = config.openclaw.defaultSessionKey ?? 'main';

// ----------------------------------------------------------------
// 生成 clawId（基于持久化的 deviceId）
// 先初始化 ClawClient 加载/生成 device identity，再读取 deviceId
// ----------------------------------------------------------------

// 临时初始化 ClawClient 以确保 .device-identity.json 存在
const _tempClawClient = new ClawClient(config.openclaw);
_tempClawClient._loadOrCreateDeviceIdentity();
const deviceId = _tempClawClient.getDeviceId();

const clawId = `claw_${deviceId.substring(0, 16)}`;

console.log(`[openHSD] 启动，clawId=${clawId}`);
console.log(`[openHSD] 云端地址：${config.cloud.wsUrl}`);
console.log(`[openHSD] OpenClaw 地址：${config.openclaw.wsUrl}`);

// ----------------------------------------------------------------
// 映射表
// reqMap:  reqId  → messageId  （发出请求后，等待 res 时用）
// runMap:  runId  → messageId  （收到 res 后，匹配后续 event 用）
// ----------------------------------------------------------------
const reqMap = new Map();  // reqId  → messageId
const runMap = new Map();  // runId  → messageId

let reqCounter = 0;
function nextReqId() {
  return `req_${Date.now()}_${++reqCounter}`;
}

// ----------------------------------------------------------------
// 连接 OpenClaw（本地）：复用已初始化的 ClawClient 实例
// ----------------------------------------------------------------
const clawClient = _tempClawClient;

clawClient.onOpen = () => {
  const deviceId = clawClient.getDeviceId();
  console.log(`[openHSD] OpenClaw 连接就绪，deviceId=${deviceId}`);
  // 通知 WsClient，下次 sync 时携带 deviceId
  wsClient.openClawDeviceId = deviceId;
};

clawClient.onClose = () => {
  console.warn('[openHSD] OpenClaw 连接断开，等待重连...');
};

/**
 * 处理来自 OpenClaw 的消息
 * 两种类型：
 *   res   — 立即响应，确认请求已收到，携带 runId
 *   event — 流式推送，携带 runId 和 state（delta / final / error / aborted）
 */
clawClient.onMessage = (msg) => {
  // ── 1. res：建立 runId → messageId 映射 ──────────────────────
  if (msg.type === 'res') {
    const messageId = reqMap.get(msg.id);
    if (!messageId) {
      console.warn('[openHSD] 收到未知 reqId 的 res，忽略：', msg.id);
      return;
    }
    reqMap.delete(msg.id);

    if (!msg.ok) {
      console.error('[openHSD] OpenClaw 拒绝请求：', msg);
      wsClient.send({ type: 'response', messageId, status: 'error', result: msg.error ?? 'OpenClaw 拒绝请求' });
      return;
    }

    const runId = msg.payload?.runId;
    if (runId) {
      runMap.set(runId, messageId);
      console.log(`[openHSD] 任务已启动：messageId=${messageId}，runId=${runId}，status=${msg.payload?.status}`);
    }
    return;
  }

  // ── 2. event → chat：流式回复 ────────────────────────────────
  if (msg.type === 'event' && msg.event === 'chat') {
    const { runId, state, message, errorMessage, seq } = msg.payload ?? {};
    const messageId = runMap.get(runId);

    if (!messageId) {
      console.warn('[openHSD] 收到未知 runId 的 event，忽略：', runId);
      return;
    }

    if (state === 'delta') {
      // 流式 chunk，转发给云端
      wsClient.send({
        type      : 'response_chunk',
        messageId,
        chunk     : message?.content ?? '',
        seq       : seq ?? 0,
        isLast    : false,
      });
      console.log(`[openHSD] delta seq=${seq}：${message?.content ?? ''}`);
      return;
    }

    if (state === 'final') {
      // 最终完成，转发完整结果并清理映射
      wsClient.send({
        type      : 'response',
        messageId,
        status    : 'completed',
        result    : message?.content ?? '',
        attachments: message?.attachments ?? [],
      });
      console.log(`[openHSD] final 转发：messageId=${messageId}，完整回复=${message?.content ?? ''}`);
      runMap.delete(runId);
      return;
    }

    if (state === 'error') {
      wsClient.send({
        type      : 'response',
        messageId,
        status    : 'error',
        result    : errorMessage ?? 'OpenClaw 执行出错',
      });
      console.error(`[openHSD] error 转发：messageId=${messageId}，error=${errorMessage}`);
      runMap.delete(runId);
      return;
    }

    if (state === 'aborted') {
      wsClient.send({
        type      : 'response',
        messageId,
        status    : 'error',
        result    : '任务被中断',
      });
      console.warn(`[openHSD] aborted 转发：messageId=${messageId}`);
      runMap.delete(runId);
      return;
    }
  }
};

clawClient.connect();

// ----------------------------------------------------------------
// 连接云端后端
// ----------------------------------------------------------------
const wsClient = new WsClient(config.cloud, clawId);

/**
 * 处理来自云端的业务消息
 * 目前只处理 type:"request"，即后端转发过来的用户消息
 */
wsClient.onMessage = (msg) => {
  console.log('[openHSD] 收到云端消息：', JSON.stringify(msg, null, 2));

  if (msg.type === 'request') {
    const { messageId, content, context } = msg;

    // OpenClaw 未连接时直接告知云端
    if (!clawClient.isConnected()) {
      console.warn('[openHSD] OpenClaw 未连接，无法处理消息 messageId=', messageId);
      wsClient.send({ type: 'response', messageId, status: 'error', result: 'OpenClaw 未连接' });
      return;
    }

    // 构造符合 OpenClaw 协议的请求
    const reqId          = nextReqId();
    const idempotencyKey = `idem_${messageId}_${Date.now()}`;
    const sessionKey     = context?.sessionKey ?? defaultSessionKey;

    const clawReq = {
      type   : 'req',
      id     : reqId,
      method : 'chat.send',
      params : {
        sessionKey,
        message        : content,
        idempotencyKey,
        ...(msg.timeout  ? { timeoutMs: msg.timeout } : {}),
        ...(context?.thinking ? { thinking: context.thinking } : {}),
      },
    };

    // 存入 reqMap，等待 res 回来时建立 runId 映射
    reqMap.set(reqId, messageId);

    const sent = clawClient.send(clawReq);
    if (!sent) {
      reqMap.delete(reqId);
      wsClient.send({ type: 'response', messageId, status: 'error', result: 'OpenClaw 发送失败' });
      return;
    }

    console.log(`[openHSD] 已转发至 OpenClaw：messageId=${messageId}，reqId=${reqId}，sessionKey=${sessionKey}`);
  }
};

wsClient.connect();

// ----------------------------------------------------------------
// 进程退出时优雅关闭
// ----------------------------------------------------------------
process.on('SIGINT',  () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));

function shutdown(signal) {
  console.log(`\n[openHSD] 收到 ${signal}，正在关闭...`);
  clawClient.destroy();
  wsClient.destroy();
  process.exit(0);
}
