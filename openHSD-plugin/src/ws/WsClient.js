import WebSocket from 'ws';

/**
 * openHSD WebSocket 客户端
 *
 * 职责：
 * - 连接云端 /ws/claw，携带 token 完成身份认证
 * - 指数退避自动重连（1s → 2s → 4s → ... 上限 60s）
 * - 每隔 heartbeatInterval 发送 ping，维持连接保活
 * - 对外暴露 onMessage 回调，供上层模块处理业务消息
 */
export class WsClient {
  /**
   * @param {object} config  cj.config.json 中的 cloud 配置段
   * @param {string} clawId  本机实例唯一标识
   */
  constructor(config, clawId) {
    this.wsUrl            = config.wsUrl;
    this.token            = config.token;
    this.heartbeatInterval = config.heartbeatInterval ?? 30000;
    this.clawId           = clawId;

    this.ws              = null;
    this.retryCount      = 0;
    this.retryTimer      = null;
    this.heartbeatTimer  = null;
    this.destroyed       = false;   // 手动关闭后不再重连

    /** 上层注册的业务消息回调：(parsedJson) => void */
    this.onMessage = null;
  }

  // ----------------------------------------------------------------
  // 公共接口
  // ----------------------------------------------------------------

  /** 启动连接 */
  connect() {
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
      console.warn('[WsClient] 发送失败：连接未就绪，数据将丢弃', data);
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

    this.ws.on('open',    ()      => this._onOpen());
    this.ws.on('message', (data)  => this._onMessage(data));
    this.ws.on('close',   (code, reason) => this._onClose(code, reason));
    this.ws.on('error',   (err)   => this._onError(err));
  }

  _onOpen() {
    console.log('[WsClient] 连接成功');
    this.retryCount = 0;
    this._startHeartbeat();

    // 重连成功后发送 sync，触发云端补偿积压消息
    this.send({ type: 'sync', clawId: this.clawId });
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
    console.warn(`[WsClient] 连接关闭：code=${code}，reason=${reason?.toString()}`);
    this._clearTimers();
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
