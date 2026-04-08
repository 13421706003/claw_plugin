import { WsClient } from './ws/WsClient.mjs';
import { ClawClient } from './ws/ClawClient.mjs';
import { extractText } from './fileExtractor.mjs';
import fs from 'fs';
import path from 'path';
import os from 'os';

export class OpenHSDService {
  constructor(config) {
    this.config = config;
    this.clawClient = null;
    this.wsClient = null;
    this.reqMap = new Map();
    this.runMap = new Map();
    this.lastSentLength = new Map();
    this.reqCounter = 0;
    this.running = false;
    this.logCallbacks = [];
    this.statusCallbacks = [];
    this._cloudConnected = false;
    this._clawConnected = false;
  }

  parseUserIdFromToken(token) {
    if (!token) return null;
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return null;
      const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString('utf8'));
      return payload?.userId ?? payload?.sub ?? null;
    } catch (e) {
      this.log('warn', '解析 Token 失败：' + e.message);
      return null;
    }
  }

  writeSessionFile(userId, clawId) {
    const sessionDir = path.join(os.homedir(), '.openhsd');
    const sessionPath = path.join(sessionDir, 'session.json');

    try {
      if (!fs.existsSync(sessionDir)) {
        fs.mkdirSync(sessionDir, { recursive: true });
      }

      const sessionData = {
        userId,
        clawId,
        updatedAt: Date.now()
      };

      fs.writeFileSync(sessionPath, JSON.stringify(sessionData, null, 2) + '\n', 'utf8');
      this.log('info', `Session 信息已写入：${sessionPath}`);
    } catch (e) {
      this.log('error', '写入 session.json 失败：' + e.message);
    }
  }

  nextReqId() {
    return `req_${Date.now()}_${++this.reqCounter}`;
  }

  log(level, message) {
    const fullMessage = `[openHSD] ${message}`;
    if (level === 'error') {
      console.error(fullMessage);
    } else if (level === 'warn') {
      console.warn(fullMessage);
    } else {
      console.log(fullMessage);
    }
    this.logCallbacks.forEach(cb => cb({ time: Date.now(), level, message: fullMessage }));
  }

  onLog(callback) {
    this.logCallbacks.push(callback);
  }

  onStatusChange(callback) {
    this.statusCallbacks.push(callback);
  }

  emitStatus() {
    const status = {
      running: this.running,
      cloudConnected: this._cloudConnected,
      clawConnected: this._clawConnected
    };
    this.statusCallbacks.forEach(cb => cb(status));
  }

  getCloudConnected() {
    return this._cloudConnected;
  }

  getClawConnected() {
    return this._clawConnected;
  }

  async start() {
    if (this.running) {
      this.log('warn', '服务已在运行中');
      return;
    }

    const userId = this.parseUserIdFromToken(this.config.cloud.token);
    const defaultSessionKey = this.config.openclaw.defaultSessionKey ?? 'main';

    this.clawClient = new ClawClient(this.config.openclaw);
    this.clawClient._loadOrCreateDeviceIdentity();
    const deviceId = this.clawClient.getDeviceId();
    const clawId = `claw_${deviceId.substring(0, 16)}`;

    this.writeSessionFile(userId, clawId);

    this.log('info', `启动，clawId=${clawId}`);
    this.log('info', `云端地址：${this.config.cloud.wsUrl}`);
    this.log('info', `OpenClaw 地址：${this.config.openclaw.wsUrl}`);

    this.clawClient.onOpen = () => {
      const devId = this.clawClient.getDeviceId();
      this.log('info', `OpenClaw 连接就绪，deviceId=${devId}`);
      this._clawConnected = true;
      this.wsClient.openClawDeviceId = devId;
      this.emitStatus();
    };

    this.clawClient.onClose = () => {
      this.log('warn', 'OpenClaw 连接断开，等待重连...');
      this._clawConnected = false;
      this.emitStatus();
    };

    this.clawClient.onMessage = (msg) => {
      if (msg.type === 'res') {
        const messageId = this.reqMap.get(msg.id);
        if (!messageId) {
          this.log('warn', '收到未知 reqId 的 res，忽略：' + msg.id);
          return;
        }
        this.reqMap.delete(msg.id);

        if (!msg.ok) {
          this.log('error', 'OpenClaw 拒绝请求：' + JSON.stringify(msg));
          this.wsClient.send({ type: 'response', messageId, status: 'error', result: msg.error ?? 'OpenClaw 拒绝请求' });
          return;
        }

        const runId = msg.payload?.runId;
        if (runId) {
          this.runMap.set(runId, messageId);
          
          this.wsClient.send({
            type: 'run_started',
            messageId,
            runId,
          });
          
          this.log('info', `任务已启动：messageId=${messageId}，runId=${runId}`);
        }
        return;
      }

      if (msg.type === 'event' && msg.event === 'chat') {
        const { runId, state, message, errorMessage, seq } = msg.payload ?? {};
        const messageId = this.runMap.get(runId);

        if (!messageId) {
          this.log('warn', '收到未知 runId 的 event，忽略：' + runId);
          return;
        }

        if (state === 'delta') {
          const content = message?.content ?? '';
          const fullText = typeof content === 'string' ? content :
                           Array.isArray(content) ? content.map(c => c.text || '').join('') : '';
          
          const prevLen = this.lastSentLength.get(messageId) ?? 0;
          const chunk = fullText.slice(prevLen);
          this.lastSentLength.set(messageId, fullText.length);
          
          this.wsClient.send({
            type: 'response_chunk',
            messageId,
            chunk,
            seq: seq ?? 0,
            isLast: false,
          });
          this.log('info', `delta seq=${seq}：${chunk}`);
          return;
        }

        if (state === 'final') {
          this.lastSentLength.delete(messageId);
          this.wsClient.send({
            type: 'response',
            messageId,
            status: 'completed',
            result: message?.content ?? '',
            attachments: message?.attachments ?? [],
          });
          this.log('info', `final 转发：messageId=${messageId}`);
          this.runMap.delete(runId);
          return;
        }

        if (state === 'error') {
          this.lastSentLength.delete(messageId);
          this.wsClient.send({
            type: 'response',
            messageId,
            status: 'error',
            result: errorMessage ?? 'OpenClaw 执行出错',
          });
          this.log('error', `error 转发：messageId=${messageId}，error=${errorMessage}`);
          this.runMap.delete(runId);
          return;
        }

        if (state === 'aborted') {
          this.lastSentLength.delete(messageId);
          this.wsClient.send({
            type: 'response',
            messageId,
            status: 'error',
            result: '任务被中断',
          });
          this.log('warn', `aborted 转发：messageId=${messageId}`);
          this.runMap.delete(runId);
          return;
        }
      }
    };

    this.clawClient.connect();

    this.wsClient = new WsClient(this.config.cloud, clawId);

    this.wsClient.onMessage = async (msg) => {
      this.log('info', '收到云端消息：' + JSON.stringify(msg, null, 2));

      if (msg.type === 'abort') {
        const { messageId, runId, sessionKey } = msg;
        
        if (!runId) {
          this.log('warn', 'abort 请求缺少 runId');
          return;
        }

        const abortReq = {
          type: 'req',
          id: this.nextReqId(),
          method: 'chat.abort',
          params: {
            sessionKey: sessionKey ?? 'main',
            runId,
          },
        };

        this.clawClient.send(abortReq);
        this.log('info', `已发送 chat.abort: runId=${runId}`);
        return;
      }

      if (msg.type === 'request') {
        const { messageId, content, context, attachments } = msg;

        if (!this.clawClient.isConnected()) {
          this.log('warn', 'OpenClaw 未连接，无法处理消息 messageId=' + messageId);
          this.wsClient.send({ type: 'response', messageId, status: 'error', result: 'OpenClaw 未连接' });
          return;
        }

        const localAttachments = [];
        let docPrefix = '';

        if (Array.isArray(attachments) && attachments.length > 0) {
          for (const att of attachments) {
            const mimeType = att.mimeType || 'application/octet-stream';
            const isImage = mimeType.startsWith('image/');
            const name = att.name || 'file';

            if (att.url) {
              try {
                this.log('info', '正在下载附件：' + att.url);
                const resp = await fetch(att.url);
                if (!resp.ok) {
                  this.log('error', `下载附件失败 HTTP ${resp.status}：${att.url}`);
                  continue;
                }
                const arrayBuffer = await resp.arrayBuffer();
                const buffer = Buffer.from(arrayBuffer);

                if (isImage) {
                  const base64 = buffer.toString('base64');
                  localAttachments.push({
                    type: 'image',
                    mimeType: mimeType,
                    content: base64,
                    fileName: name,
                  });
                  this.log('info', `图片附件就绪：mime=${mimeType}`);
                } else {
                  const textContent = await extractText(buffer, mimeType, name);

                  if (textContent && textContent.trim() && !textContent.startsWith('[')) {
                    const maxChars = 100000;
                    const truncated = textContent.length > maxChars
                      ? textContent.substring(0, maxChars) + '\n\n... [文档内容过长，已截断]'
                      : textContent;
                    docPrefix += `\n<file name="${name}" mime="${mimeType}">\n${truncated}\n</file>\n`;
                    this.log('info', `文档提取成功：name=${name}`);
                  } else {
                    docPrefix += `\n[附件：${name}（${mimeType}），${textContent || '文本提取为空'}]\n`;
                    this.log('warn', `文档提取失败：name=${name}`);
                  }
                }
              } catch (e) {
                this.log('error', '下载附件异常：' + e.message);
              }
            } else if (att.type === 'image' && att.base64) {
              const matches = att.base64.match(/^data:([a-zA-Z0-9]+\/[a-zA-Z0-9+.-]+);base64,(.+)$/);
              if (matches) {
                localAttachments.push({
                  type: 'image',
                  mimeType: matches[1],
                  content: matches[2],
                });
                this.log('info', '附件(Base64)已处理');
              }
            }
          }
        }

        const finalMessage = docPrefix ? docPrefix + '\n' + content : content;

        const reqId = this.nextReqId();
        const idempotencyKey = `idem_${messageId}_${Date.now()}`;
        const sessionKey = context?.sessionKey ?? defaultSessionKey;

        const clawReq = {
          type: 'req',
          id: reqId,
          method: 'chat.send',
          params: {
            sessionKey,
            message: finalMessage,
            idempotencyKey,
            ...(msg.timeout ? { timeoutMs: msg.timeout } : {}),
            ...(context?.thinking ? { thinking: context.thinking } : {}),
            ...(localAttachments.length ? { attachments: localAttachments } : {}),
          },
        };

        this.reqMap.set(reqId, messageId);

        const sent = this.clawClient.send(clawReq);
        if (!sent) {
          this.reqMap.delete(reqId);
          this.wsClient.send({ type: 'response', messageId, status: 'error', result: 'OpenClaw 发送失败' });
          return;
        }

        this.log('info', `已转发至 OpenClaw：messageId=${messageId}，reqId=${reqId}`);
      }
    };

    this.wsClient.onOpen = () => {
      this.log('info', '云端连接就绪');
      this._cloudConnected = true;
      this.emitStatus();
    };

    this.wsClient.onClose = () => {
      this.log('warn', '云端连接断开，等待重连...');
      this._cloudConnected = false;
      this.emitStatus();
    };

    this.wsClient.connect();
    this.running = true;
    this.emitStatus();
  }

  stop() {
    if (!this.running) {
      return;
    }

    this.log('info', '正在停止服务...');

    if (this.clawClient) {
      this.clawClient.destroy();
      this.clawClient = null;
    }

    if (this.wsClient) {
      this.wsClient.destroy();
      this.wsClient = null;
    }

    this.reqMap.clear();
    this.runMap.clear();
    this.lastSentLength.clear();
    this._cloudConnected = false;
    this._clawConnected = false;
    this.running = false;
    this.emitStatus();

    this.log('info', '服务已停止');
  }

  isRunning() {
    return this.running;
  }
}
