import WebSocket from 'ws';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';
import os from 'os';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

/**
 * 自动从 OpenClaw 本地配置文件读取 gateway token
 * 路径：~/.openclaw/openclaw.json → gateway.auth.token
 */
function loadOpenClawGatewayToken() {
  try {
    const configPath = path.join(os.homedir(), '.openclaw', 'openclaw.json');
    if (!fs.existsSync(configPath)) return null;
    const raw = fs.readFileSync(configPath, 'utf8');
    const config = JSON.parse(raw);
    const token = config?.gateway?.auth?.token;
    if (token && typeof token === 'string') {
      console.log('[ClawClient] 已从 OpenClaw 配置自动读取 gateway token');
      return token;
    }
  } catch (e) {
    console.warn('[ClawClient] 读取 OpenClaw gateway token 失败：', e.message);
  }
  return null;
}

// ED25519 SPKI 前缀（用于 DER 编码）
const ED25519_SPKI_PREFIX = Buffer.from([
  0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70,
  0x03, 0x21, 0x00
]);

/**
 * OpenClaw WebSocket 客户端
 * 实现完整的 Gateway 协议握手（device identity + challenge 签名）
 */
export class ClawClient {
  constructor(config) {
    this.wsUrl             = config.wsUrl;
    // 优先用配置文件里的 token，没有则自动读取 OpenClaw 本地配置
    this.token             = config.token ?? loadOpenClawGatewayToken();
    this.reconnectMaxDelay = 60000;
    this.pingInterval      = config.pingInterval ?? 20000;

    // Device identity
    this.identityPath      = config.identityPath ?? path.join(__dirname, '../../.device-identity.json');
    this.deviceIdentity    = null;

    // 握手状态
    this.challenge         = null;
    this.handshakeComplete = false;

    this.ws          = null;
    this.retryCount  = 0;
    this.retryTimer  = null;
    this.pingTimer   = null;
    this.destroyed   = false;

    this.onMessage = null;
    this.onOpen = null;
    this.onClose = null;
  }

  // ----------------------------------------------------------------
  // 公共接口
  // ----------------------------------------------------------------

  connect() {
    this.destroyed = false;
    this._loadOrCreateDeviceIdentity();
    this._connect();
  }

  destroy() {
    this.destroyed = true;
    this._clearTimers();
    if (this.ws) {
      this.ws.terminate();
      this.ws = null;
    }
    console.log('[ClawClient] 已主动销毁');
  }

  send(data) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn('[ClawClient] 发送失败：连接未就绪');
      return false;
    }
    if (!this.handshakeComplete) {
      console.warn('[ClawClient] 发送失败：握手未完成');
      return false;
    }
    const payload = typeof data === 'string' ? data : JSON.stringify(data);
    this.ws.send(payload);
    return true;
  }

  isConnected() {
    return this.ws !== null && this.ws.readyState === WebSocket.OPEN && this.handshakeComplete;
  }

  // ----------------------------------------------------------------
  // Device Identity 管理
  // ----------------------------------------------------------------

  _loadOrCreateDeviceIdentity() {
    try {
      if (fs.existsSync(this.identityPath)) {
        const raw = fs.readFileSync(this.identityPath, 'utf8');
        const parsed = JSON.parse(raw);
        if (parsed?.version === 1 && parsed.deviceId && parsed.publicKeyPem && parsed.privateKeyPem) {
          this.deviceIdentity = {
            deviceId: parsed.deviceId,
            publicKeyPem: parsed.publicKeyPem,
            privateKeyPem: parsed.privateKeyPem
          };
          console.log('[ClawClient] 已加载设备身份：', parsed.deviceId.substring(0, 16) + '...');
          return;
        }
      }
    } catch (e) {
      console.warn('[ClawClient] 读取设备身份失败，将重新生成：', e.message);
    }

    // 生成新的密钥对
    const { publicKeyPem, privateKeyPem, deviceId } = this._generateIdentity();
    this.deviceIdentity = { deviceId, publicKeyPem, privateKeyPem };

    // 持久化
    const stored = {
      version: 1,
      deviceId,
      publicKeyPem,
      privateKeyPem,
      createdAtMs: Date.now()
    };
    fs.mkdirSync(path.dirname(this.identityPath), { recursive: true });
    fs.writeFileSync(this.identityPath, JSON.stringify(stored, null, 2) + '\n', { mode: 0o600 });
    console.log('[ClawClient] 已生成新设备身份：', deviceId.substring(0, 16) + '...');
  }

  _generateIdentity() {
    const { publicKey, privateKey } = crypto.generateKeyPairSync('ed25519');
    const publicKeyPem = publicKey.export({ type: 'spki', format: 'pem' }).toString();
    const privateKeyPem = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
    const deviceId = this._fingerprintPublicKey(publicKeyPem);
    return { publicKeyPem, privateKeyPem, deviceId };
  }

  _fingerprintPublicKey(publicKeyPem) {
    const raw = this._derivePublicKeyRaw(publicKeyPem);
    return crypto.createHash('sha256').update(raw).digest('hex');
  }

  _derivePublicKeyRaw(publicKeyPem) {
    const key = crypto.createPublicKey(publicKeyPem);
    return key.export({ type: 'spki', format: 'der' }).slice(-32);
  }

  _publicKeyRawBase64Url(publicKeyPem) {
    const raw = this._derivePublicKeyRaw(publicKeyPem);
    return this._base64UrlEncode(raw);
  }

  _base64UrlEncode(buffer) {
    return buffer.toString('base64')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  }

  // ----------------------------------------------------------------
  // 签名
  // ----------------------------------------------------------------

  _signDevicePayload(privateKeyPem, payload) {
    const key = crypto.createPrivateKey(privateKeyPem);
    const sig = crypto.sign(null, Buffer.from(payload, 'utf8'), key);
    return this._base64UrlEncode(sig);
  }

  _buildDeviceAuthPayloadV3(params) {
    const scopes = (params.scopes || []).join(',');
    const token = params.token ?? '';
    const platform = (params.platform || '').toLowerCase().trim();
    const deviceFamily = (params.deviceFamily || '').toLowerCase().trim();
    return [
      'v3',
      params.deviceId,
      params.clientId,
      params.clientMode,
      params.role,
      scopes,
      String(params.signedAtMs),
      token,
      params.nonce,
      platform,
      deviceFamily
    ].join('|');
  }

  // ----------------------------------------------------------------
  // 连接管理
  // ----------------------------------------------------------------

  _connect() {
    if (this.destroyed) return;

    console.log(`[ClawClient] 正在连接 OpenClaw：${this.wsUrl}（第 ${this.retryCount} 次尝试）`);

    this.challenge = null;
    this.handshakeComplete = false;

    this.ws = new WebSocket(this.wsUrl);

    this.ws.on('open',    ()     => this._onOpen());
    this.ws.on('message', (data) => this._onMessage(data));
    this.ws.on('close',   (code, reason) => this._onClose(code, reason));
    this.ws.on('error',   (err)  => this._onError(err));
  }

  _onOpen() {
    console.log('[ClawClient] TCP 连接已建立，等待 connect.challenge...');
    this.retryCount = 0;
  }

  _onMessage(raw) {
    const text = raw.toString();
    let data;
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }

    // 1. 收到 challenge
    if (data?.type === 'event' && data?.event === 'connect.challenge') {
      this.challenge = data.payload;
      console.log('[ClawClient] 收到 challenge，nonce=', this.challenge?.nonce?.substring(0, 8) + '...');
      this._sendConnectRequest();
      return;
    }

    // 2. connect 响应
    if (data?.type === 'res' && data?.id === 'connect_handshake') {
      if (data.ok) {
        this.handshakeComplete = true;
        console.log('[ClawClient] 握手成功！协议版本=', data.payload?.protocol);
        this._startPing();
        if (typeof this.onOpen === 'function') this.onOpen();
      } else {
        console.error('[ClawClient] 握手失败：', data.error);
        this.ws.close(1008, 'Handshake failed');
      }
      return;
    }

    // 握手完成后的业务消息
    if (!this.handshakeComplete) {
      console.warn('[ClawClient] 收到消息但握手未完成，忽略');
      return;
    }

    if (typeof this.onMessage === 'function') {
      this.onMessage(data);
    }
  }

  _onClose(code, reason) {
    console.warn(`[ClawClient] 连接断开：code=${code}，reason=${reason?.toString()}`);
    this._clearTimers();
    this.handshakeComplete = false;
    if (typeof this.onClose === 'function') this.onClose(code);
    this._scheduleRetry();
  }

  _onError(err) {
    console.error('[ClawClient] 连接错误：', err.message);
  }

  // ----------------------------------------------------------------
  // 发送 connect 握手请求
  // ----------------------------------------------------------------

  _sendConnectRequest() {
    if (!this.challenge || !this.deviceIdentity) {
      console.error('[ClawClient] 缺少 challenge 或 deviceIdentity');
      return;
    }

    const nonce = this.challenge.nonce;
    const signedAtMs = Date.now();
    const role = 'operator';
    const scopes = ['operator.read', 'operator.write'];
    const platform = process.platform;

    // 构建签名 payload（token 需要绑定进签名）
    const payload = this._buildDeviceAuthPayloadV3({
      deviceId: this.deviceIdentity.deviceId,
      clientId: 'gateway-client',
      clientMode: 'cli',
      role,
      scopes,
      signedAtMs,
      token: this.token ?? null,
      nonce,
      platform,
      deviceFamily: null
    });

    // 签名
    const signature = this._signDevicePayload(this.deviceIdentity.privateKeyPem, payload);

    // device 字段
    const device = {
      id: this.deviceIdentity.deviceId,
      publicKey: this._publicKeyRawBase64Url(this.deviceIdentity.publicKeyPem),
      signature,
      signedAt: signedAtMs,
      nonce
    };

    // connect 请求
    const connectReq = {
      type: 'req',
      id: 'connect_handshake',
      method: 'connect',
      params: {
        minProtocol: 3,
        maxProtocol: 3,
        client: {
          id: 'gateway-client',
          version: '1.0.0',
          platform,
          mode: 'cli'
        },
        role,
        scopes,
        caps: [],
        commands: [],
        permissions: {},
        locale: 'zh-CN',
        userAgent: 'openHSD-plugin/1.0.0',
        auth: this.token ? { token: this.token } : undefined,
        device
      }
    };

    this.ws.send(JSON.stringify(connectReq));
    console.log('[ClawClient] 已发送 connect 握手请求（带 device identity）');
  }

  // ----------------------------------------------------------------
  // 心跳
  // ----------------------------------------------------------------

  _startPing() {
    this._clearPingTimer();
    this.pingTimer = setInterval(() => {
      if (this.ws && this.ws.readyState === WebSocket.OPEN) {
        this.ws.ping();
      }
    }, this.pingInterval);
  }

  _clearPingTimer() {
    if (this.pingTimer) {
      clearInterval(this.pingTimer);
      this.pingTimer = null;
    }
  }

  _clearTimers() {
    this._clearPingTimer();
    if (this.retryTimer) {
      clearTimeout(this.retryTimer);
      this.retryTimer = null;
    }
  }

  // ----------------------------------------------------------------
  // 重连
  // ----------------------------------------------------------------

  _scheduleRetry() {
    if (this.destroyed) return;

    const delay = Math.min(1000 * Math.pow(2, this.retryCount), this.reconnectMaxDelay);
    this.retryCount++;
    console.log(`[ClawClient] ${delay / 1000}s 后重连（第 ${this.retryCount} 次）`);
    this.retryTimer = setTimeout(() => this._connect(), delay);
  }
}
