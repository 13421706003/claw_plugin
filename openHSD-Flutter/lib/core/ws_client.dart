import 'dart:async';
import 'dart:convert';

import 'package:web_socket_channel/web_socket_channel.dart';

import '../core/app_config.dart';

typedef WsChunkCallback = void Function(String messageId, String chunk, int seq);
typedef WsFinalCallback = void Function(String messageId, String status, String result, dynamic attachments);
typedef WsStatusCallback = void Function(bool connected);

class WsAiClient {
  final WsChunkCallback onChunk;
  final WsFinalCallback onFinal;
  final WsStatusCallback onStatus;

  WebSocketChannel? _channel;
  StreamSubscription? _sub;
  Timer? _heartbeatTimer;
  Timer? _reconnectTimer;

  int _retryCount = 0;
  bool _manualClose = false;
  int? _currentUserId;

  WsAiClient({
    required this.onChunk,
    required this.onFinal,
    required this.onStatus,
  });

  Future<void> connect(int userId) async {
    _currentUserId = userId;
    _manualClose = false;

    // Already connected (or reconnecting) -> skip.
    if (_channel != null) return;

    final url = '${AppConfig.wsBaseUrl}/ws/web/$userId';
    _connectInternal(url);
  }

  void _connectInternal(String url) {
    onStatus(true);

    _sub?.cancel();
    _channel = WebSocketChannel.connect(Uri.parse(url));

    _sub = _channel!.stream.listen(
      (data) {
        try {
          final json = jsonDecode(data);
          if (json is Map) {
            final type = json['type']?.toString();
            if (type == null) return;

            if (type == 'pong') return;

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
      final url = '${AppConfig.wsBaseUrl}/ws/web/$userId';
      _connectInternal(url);
    });
  }

  int _calcReconnectDelayMs() {
    final ms = 1000 * (1 << _retryCount);
    return ms > 60000 ? 60000 : ms;
  }
}

