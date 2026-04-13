import 'dart:async';
import 'dart:convert';

import 'package:web_socket_channel/web_socket_channel.dart';

import '../core/app_config.dart';

/// 兼容文本帧与 UTF-8 二进制帧；已是 Map 时直接返回（便于测试注入）。
dynamic _decodeWsPayload(dynamic data) {
  if (data is String) return jsonDecode(data);
  if (data is List<int>) return jsonDecode(utf8.decode(data));
  if (data is Map) return data;
  return null;
}

int? _parseOptionalInt(dynamic v) {
  if (v == null) return null;
  if (v is int) return v;
  if (v is num) return v.toInt();
  return int.tryParse(v.toString());
}

typedef WsChunkCallback =
    void Function(String messageId, String chunk, int seq);
typedef WsFinalCallback =
    void Function(
      String messageId,
      String status,
      String result,
      dynamic attachments,
    );
typedef WsStatusCallback = void Function(bool connected);
typedef WsFilePushCallback =
    void Function(
      String messageId,
      String clawId,
      String fileUrl,
      String fileName,
      String fileType,
      int? fileSize,
    );

class WsAiClient {
  final WsChunkCallback onChunk;
  final WsFinalCallback onFinal;
  final WsStatusCallback onStatus;
  final WsFilePushCallback? onFilePush;

  WebSocketChannel? _channel;
  StreamSubscription? _sub;
  Timer? _heartbeatTimer;
  Timer? _reconnectTimer;

  int _retryCount = 0;
  bool _manualClose = false;
  int? _currentUserId;
  String? _currentTabId;

  WsAiClient({
    required this.onChunk,
    required this.onFinal,
    required this.onStatus,
    this.onFilePush,
  });

  Future<void> connect(int userId, {String? tabId}) async {
    _currentUserId = userId;
    _currentTabId = tabId;
    _manualClose = false;

    // Already connected (or reconnecting) -> skip.
    if (_channel != null) return;

    final url = _buildWsUrl(userId, tabId: tabId);
    _connectInternal(url);
  }

  String _buildWsUrl(int userId, {String? tabId}) {
    final base = _normalizeWsBase(AppConfig.wsBaseUrl);
    final suffix = (tabId != null && tabId.isNotEmpty)
        ? '/web/$userId/$tabId'
        : '/web/$userId';
    return '$base$suffix';
  }

  String _normalizeWsBase(String raw) {
    var s = raw.trim();
    if (s.endsWith('#')) s = s.substring(0, s.length - 1);
    while (s.endsWith('/')) {
      s = s.substring(0, s.length - 1);
    }
    if (!s.contains('://')) s = 'ws://$s';

    final parsed = Uri.tryParse(s);
    if (parsed == null || parsed.host.isEmpty) {
      return s
          .replaceFirst(RegExp(r'^https://'), 'wss://')
          .replaceFirst(RegExp(r'^http://'), 'ws://');
    }

    final scheme = switch (parsed.scheme) {
      'http' => 'ws',
      'https' => 'wss',
      'ws' => 'ws',
      'wss' => 'wss',
      _ => 'wss',
    };
    final port = (parsed.hasPort && parsed.port > 0) ? ':${parsed.port}' : '';
    final path = parsed.path.endsWith('/')
        ? parsed.path.substring(0, parsed.path.length - 1)
        : parsed.path;
    return '$scheme://${parsed.host}$port$path';
  }

  void _connectInternal(String url) {
    _sub?.cancel();
    try {
      _channel = WebSocketChannel.connect(Uri.parse(url));
    } catch (_) {
      _handleClosed();
      return;
    }

    _sub = _channel!.stream.listen(
      (data) {
        try {
          final decoded = _decodeWsPayload(data);
          if (decoded is! Map) return;
          final json = Map<dynamic, dynamic>.from(decoded);
          final type = json['type']?.toString();
          if (type == null) return;

          if (type == 'pong') return;
          if (type == 'connected') {
            onStatus(true);
            return;
          }

          if (type == 'response_chunk') {
            final messageId = json['messageId']?.toString() ?? '';
            final chunk = json['chunk']?.toString() ?? '';
            final seq = (json['seq'] as num?)?.toInt() ?? 0;
            onChunk(messageId, chunk, seq);
            return;
          }

          if (type == 'response') {
            final messageId = json['messageId']?.toString() ?? '';
            final status = json['status']?.toString() ?? 'completed';
            final result = json['result']?.toString() ?? '';
            final attachments = json['attachments'];
            onFinal(messageId, status, result, attachments);
            return;
          }

          if (type == 'file_push') {
            final messageId = json['messageId']?.toString() ?? '';
            final clawId = json['clawId']?.toString() ?? '';
            final fileUrl = json['fileUrl']?.toString() ?? '';
            final fileName = json['fileName']?.toString() ?? 'file';
            final fileType =
                json['fileType']?.toString() ?? 'application/octet-stream';
            final fileSize = _parseOptionalInt(json['fileSize']);
            onFilePush?.call(
              messageId,
              clawId,
              fileUrl,
              fileName,
              fileType,
              fileSize,
            );
            return;
          }
        } catch (_) {
          // Ignore non-JSON.
        }
      },
      onError: (_) {
        _handleClosed();
      },
      onDone: () {
        _handleClosed();
      },
      cancelOnError: true,
    );

    // Treat TCP/WebSocket upgrade as online even if server doesn't emit "connected".
    onStatus(true);
    _retryCount = 0;
    _startHeartbeat();
  }

  void _startHeartbeat() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      sendRaw({'type': 'ping'});
    });
  }

  void sendRaw(Map<String, dynamic> data) {
    try {
      _channel?.sink.add(jsonEncode(data));
    } catch (_) {}
  }

  void disconnect() {
    _manualClose = true;
    _reconnectTimer?.cancel();
    _reconnectTimer = null;
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;

    try {
      _channel?.sink.close();
    } catch (_) {}
    _sub?.cancel();
    _sub = null;
    _channel = null;
    onStatus(false);
  }

  void _handleClosed() {
    _heartbeatTimer?.cancel();
    _heartbeatTimer = null;
    _channel = null;
    _sub = null;
    onStatus(false);

    if (_manualClose) return;

    final userId = _currentUserId;
    if (userId == null) return;

    final delayMs = _calcReconnectDelayMs();
    _reconnectTimer?.cancel();
    _reconnectTimer = Timer(Duration(milliseconds: delayMs), () {
      _retryCount = _retryCount + 1;
      final url = _buildWsUrl(userId, tabId: _currentTabId);
      _connectInternal(url);
    });
  }

  int _calcReconnectDelayMs() {
    final ms = 1000 * (1 << _retryCount);
    return ms > 60000 ? 60000 : ms;
  }
}
