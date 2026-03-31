class AppConfig {
  /// 后端主机与端口（不含协议）。默认使用线上域名。
  ///
  /// 真机调试示例：
  /// `flutter run --dart-define=OPENHSD_HOST=192.168.1.5:9081`
  static const String _hostPort = String.fromEnvironment(
    'OPENHSD_HOST',
    defaultValue: 'www.huashidai1.com',
  );

  /// 可指定完整 HTTP base（例如 `https://huashidai1.com/hsdclaw-api`）。
  /// 非空时优先于根据 [OPENHSD_HOST] 拼接地址。
  static const String _httpBaseOverride = String.fromEnvironment(
    'OPENHSD_HTTP_BASE',
    defaultValue: 'https://www.huashidai1.com/hsdclaw-api',
  );

  /// 可指定完整 WS base（例如 `wss://huashidai1.com/hsdclaw-ws`）。
  /// 非空时优先于根据 [OPENHSD_HOST] 拼接地址。
  static const String _wsBaseOverride = String.fromEnvironment(
    'OPENHSD_WS_BASE',
    defaultValue: 'wss://www.huashidai1.com/hsdclaw-ws',
  );

  static String get httpBaseUrl =>
      _httpBaseOverride.isNotEmpty ? _httpBaseOverride : 'http://$_hostPort';

  static String get wsBaseUrl =>
      _wsBaseOverride.isNotEmpty ? _wsBaseOverride : 'ws://$_hostPort';

  static const String apiPrefix = '/api';
}
