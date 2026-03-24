class AppConfig {
  /// 后端主机与端口（不含 `http://`）。默认 `10.0.2.2:9081` 供 Android 模拟器访问本机服务。
  ///
  /// 真机调试示例：
  /// `flutter run --dart-define=OPENHSD_HOST=192.168.1.5:9081`
  static const String _hostPort = String.fromEnvironment(
    'OPENHSD_HOST',
    // defaultValue: '10.0.2.2:9081',
    defaultValue: '192.168.110.116:9081',
  );

  /// 若使用 https，可指定完整 origin（非空时优先于根据 [OPENHSD_HOST] 拼接的 http 地址）
  static const String _httpBaseOverride = String.fromEnvironment(
    'OPENHSD_HTTP_BASE',
    defaultValue: '',
  );

  /// 若使用 wss，可指定完整 origin（非空时优先于根据 [OPENHSD_HOST] 拼接的 ws 地址）
  static const String _wsBaseOverride = String.fromEnvironment(
    'OPENHSD_WS_BASE',
    defaultValue: '',
  );

  static String get httpBaseUrl =>
      _httpBaseOverride.isNotEmpty ? _httpBaseOverride : 'http://$_hostPort';

  static String get wsBaseUrl =>
      _wsBaseOverride.isNotEmpty ? _wsBaseOverride : 'ws://$_hostPort';

  static const String apiPrefix = '/api';
}
