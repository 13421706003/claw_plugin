import WebSocket from 'ws';

// 认证失败的 WS 关闭码，不重连
const AUTH_FAIL_CODES = new Set([4001, 4008, 1008]);

/**
 * openHSD WebSocket 客户端
 *
 * 职责：
 * - 连接云端 /ws/claw，携带 JWT token 完成身份认证
 * - 指数退避自动重连（1s → 2s → 4s → ... 上限 60s）
 * - 认证失败（401/4008/1008）时停止重连，打印明确提示
 * - 每隔 heartbeatInterval 发送 ping，维持连接保活
 */
export class WsClient {
  /**
   * @param {object} config  cj.config.json 中的 cloud 配置段
   * @param {string} clawId  本机实例唯一标识
   */
  constructor(config, clawId) {
    this.wsUrl             = config.wsUrl;
    this.token             = config.token;
    this.heartbeatInterval = config.heartbeatInterval ?? 30000;
    this.clawId            = clawId;

    this.ws             = null;
    this.retryCount     = 0;
    this.retryTimer     = null;
    this.heartbeatTimer = null;
    this.destroyed      = false;

    /** 上层注册的业务消息回调：(parsedJson) => void */
    this.onMessage = null;
    this.onOpen = null;
    this.onClose = null;
  }

  // ----------------------------------------------------------------
  // 公共接口
  // ----------------------------------------------------------------

  /** 启动连接（先校验 token 是否存在） */
  connect() {
    if (!this.token || this.token.trim() === '') {
      console.error('');
      console.error('╔══════════════════════════════════════════════════════╗');
      console.error('║              openHSD Plugin 启动失败                  ║');
      console.error('╠══════════════════════════════════════════════════════╣');
      console.error('║  cloud.token 未配置！                                  ║');
      console.error('║                                                        ║');
      console.error('║  请按以下步骤操作：                                    ║');
      console.error('║  1. 打开浏览器访问 openHSD 前端                        ║');
      console.error('║  2. 登录账号后复制 JWT Token                           ║');
      console.error('║  3. 将 Token 填入 cj.config.json 的                   ║');
      console.error('║     cloud.token 字段                                   ║');
      console.error('║  4. 重启插件                                            ║');
      console.error('╚══════════════════════════════════════════════════════╝');
      console.error('');
      process.exit(1);
    }

    this.destroyed = false;
    this._connect();
  }

  /** 主动销毁，不再重连 */
  destroy() {
    this.destroyed = true;
    this._clearTimers();
    if (this.ws) {
      this.ws.terminate();
      this.ws = null;
    }
    console.log('[WsClient] 已主动销毁');
  }

  /**
   * 发送 JSON 消息
   * @param {object} data
   * @returns {boolean} 是否发送成功
   */
  send(data) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn('[WsClient] 发送失败：连接未就绪，数据将丢弃');
      return false;
    }
    this.ws.send(JSON.stringify(data));
    return true;
  }

  // ----------------------------------------------------------------
  // 内部：连接管理
  // ----------------------------------------------------------------

  _connect() {
    if (this.destroyed) return;

    const url = `${this.wsUrl}?token=${encodeURIComponent(this.token)}`;
    console.log(`[WsClient] 正在连接：${this.wsUrl}（第 ${this.retryCount} 次尝试）`);

    this.ws = new WebSocket(url);

    this.ws.on('open',    ()                  => this._onOpen());
    this.ws.on('message', (data)              => this._onMessage(data));
    this.ws.on('close',   (code, reason)      => this._onClose(code, reason));
    this.ws.on('error',   (err)               => this._onError(err));
  }

  _onOpen() {
    console.log('[WsClient] 连接成功');
    this.retryCount = 0;
    this._startHeartbeat();
    
    // 调用外部回调
    if (typeof this.onOpen === 'function') {
      this.onOpen();
    }
    
    // 重连成功后发送 sync，触发云端补偿积压消息
    // 携带 OpenClaw 的 deviceId，让后端知道是哪台机器
    this.send({
      type             : 'sync',
      clawId           : this.clawId,
      openClawDeviceId : this.openClawDeviceId ?? null,
    });
  }

  _onMessage(raw) {
    let json;
    try {
      json = JSON.parse(raw.toString());
    } catch {
      console.warn('[WsClient] 收到非 JSON 消息：', raw.toString());
      return;
    }

    const { type } = json;

    // 框架层消息：自行处理，不上抛
    if (type === 'pong') {
      console.debug('[WsClient] pong 收到，serverTime=', json.serverTime);
      return;
    }
    if (type === 'connected') {
      console.log('[WsClient] 云端确认连接：userId=', json.userId);
      return;
    }
    if (type === 'sync_ack') {
      console.log('[WsClient] sync_ack：pendingCount=', json.pendingCount);
      return;
    }

    // 业务消息：上抛给 MessageDispatcher
    if (typeof this.onMessage === 'function') {
      this.onMessage(json);
    }
  }

  _onClose(code, reason) {
    const reasonStr = reason?.toString() || '';
    console.warn(`[WsClient] 连接关闭：code=${code}，reason=${reasonStr}`);
    this._clearTimers();
    
    // 调用外部回调
    if (typeof this.onClose === 'function') {
      this.onClose(code, reason);
    }

    // 认证失败：不重连，打印明确错误
    if (AUTH_FAIL_CODES.has(code)) {
      console.error('');
      console.error('╔══════════════════════════════════════════════════════╗');
      console.error('║              Token 认证失败，停止重连                  ║');
      console.error('╠══════════════════════════════════════════════════════╣');
      console.error('║  可能原因：                                            ║');
      console.error('║  1. cj.config.json 中的 token 已过期或不正确          ║');
      console.error('║  2. 请重新登录前端获取新 token 后更新配置文件          ║');
      console.error('╚══════════════════════════════════════════════════════╝');
      console.error('');
      this.destroyed = true;
      return;
    }

    this._scheduleRetry();
  }

  _onError(err) {
    console.error('[WsClient] 连接错误：', err.message);
    // error 后通常紧跟 close 事件，重连逻辑在 _onClose 中处理
  }

  // ----------------------------------------------------------------
  // 内部：指数退避重连
  // ----------------------------------------------------------------

  _scheduleRetry() {
    if (this.destroyed) return;

    // 指数退避：1000ms * 2^retryCount，最大 60s
    const delay = Math.min(1000 * Math.pow(2, this.retryCount), 60000);
    this.retryCount++;

    console.log(`[WsClient] ${delay / 1000}s 后重试（第 ${this.retryCount} 次）`);
    this.retryTimer = setTimeout(() => this._connect(), delay);
  }

  // ----------------------------------------------------------------
  // 内部：心跳
  // ----------------------------------------------------------------

  _startHeartbeat() {
    this._clearHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      const sent = this.send({ type: 'ping', clawId: this.clawId });
      if (sent) {
        console.debug('[WsClient] ping 已发送');
      }
    }, this.heartbeatInterval);
  }

  _clearHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  _clearTimers() {
    this._clearHeartbeat();
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }
}
