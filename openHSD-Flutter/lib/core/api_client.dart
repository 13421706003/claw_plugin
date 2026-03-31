import 'package:dio/dio.dart';
import 'package:http_parser/http_parser.dart';

import '../core/app_config.dart';
import '../models/openhsd_models.dart';

class ApiClient {
  final Dio _dio;

  String? _token;

  ApiClient({String? token})
    // 勿在 BaseOptions 写死 Content-Type：multipart 上传需由 Dio 自动带 boundary
    : _dio = Dio(
        BaseOptions(
          baseUrl: AppConfig.httpBaseUrl,
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 30),
        ),
      ) {
    _token = token;
  }

  void updateToken(String? token) {
    _token = token;
  }

  Future<Map<String, dynamic>> _requireSuccess(Response res) async {
    final data = res.data;
    if (data is Map<String, dynamic>) {
      final success = data['success'];
      if (success == true) return data;
      final msg = data['message']?.toString();
      final code = data['code']?.toString();
      final fallback =
          '请求失败'
          '${code != null && code.isNotEmpty ? ' (code=$code)' : ''}'
          '${res.statusCode != null ? ', http=${res.statusCode}' : ''}';
      final detail = (msg != null && msg.isNotEmpty) ? msg : fallback;
      throw Exception(detail);
    }
    throw Exception(
      '响应格式异常'
      '${res.statusCode != null ? ' (http=${res.statusCode})' : ''}: ${res.data}',
    );
  }

  Options _authOptions() {
    if (_token == null || _token!.isEmpty) {
      return Options(headers: <String, String>{});
    }
    return Options(headers: {'Authorization': 'Bearer $_token'});
  }

  Future<OpenHsdUser> login(String username, String password) async {
    final res = await _dio.post(
      '${AppConfig.apiPrefix}/auth/login',
      data: {'username': username, 'password': password},
    );
    final body = await _requireSuccess(res);
    return OpenHsdUser(
      userId: int.tryParse(body['userId']?.toString() ?? '') ?? 0,
      username: body['username']?.toString() ?? '',
      token: body['token']?.toString() ?? '',
    );
  }

  Future<void> register(String username, String password) async {
    final res = await _dio.post(
      '${AppConfig.apiPrefix}/auth/register',
      data: {'username': username, 'password': password},
    );
    await _requireSuccess(res);
  }

  Future<double> getRechargeExchangeRate() async {
    final res = await _dio.get(
      '${AppConfig.apiPrefix}/recharge/exchange-rate',
      options: _authOptions(),
    );
    final data = res.data;
    if (data is Map<String, dynamic>) {
      final rate = data['rate'];
      if (rate is num) return rate.toDouble();
      final parsed = double.tryParse(rate?.toString() ?? '');
      if (parsed != null) return parsed;
    }
    throw Exception('获取汇率失败');
  }

  Future<Map<String, dynamic>> getRechargeKeyInfo() async {
    final res = await _dio.get(
      '${AppConfig.apiPrefix}/recharge/key-info',
      options: _authOptions(),
    );
    return _requireSuccess(res);
  }

  Future<Map<String, dynamic>> createRechargeOrder({
    required double amountUsd,
    required String paymentChannel,
    String paymentType = 'NATIVE',
  }) async {
    final res = await _dio.post(
      '${AppConfig.apiPrefix}/recharge/create',
      data: {
        'amountUsd': amountUsd.toStringAsFixed(2),
        'paymentChannel': paymentChannel,
        'paymentType': paymentType,
      },
      options: _authOptions(),
    );
    return _requireSuccess(res);
  }

  Future<Map<String, dynamic>> getRechargeOrderStatus(String orderNo) async {
    final res = await _dio.get(
      '${AppConfig.apiPrefix}/recharge/status/$orderNo',
      options: _authOptions(),
    );
    return _requireSuccess(res);
  }

  Future<List<Map<String, dynamic>>> getRechargeHistory({
    int limit = 10,
  }) async {
    final res = await _dio.get(
      '${AppConfig.apiPrefix}/recharge/history',
      queryParameters: {'limit': limit},
      options: _authOptions(),
    );
    final body = await _requireSuccess(res);
    final orders = (body['orders'] as List?) ?? const [];
    return orders.map((e) => (e as Map).cast<String, dynamic>()).toList();
  }

  Future<List<ClawDevice>> getClawStatus(int userId) async {
    final res = await _dio.get(
      '${AppConfig.apiPrefix}/claw/status',
      queryParameters: {'userId': userId},
      options: _authOptions(),
    );

    // 兼容后端 /api/claw/status 返回：
    // { userId, online, clawCount, clawList, totalOnline }
    // 该接口当前不带 success 字段，因此优先按 clawList 解析。
    final raw = res.data;
    if (raw is Map<String, dynamic> && raw.containsKey('clawList')) {
      final list = (raw['clawList'] as List?) ?? const [];
      return list
          .map((e) => ClawDevice.fromJson((e as Map).cast<String, dynamic>()))
          .toList();
    }

    final body = await _requireSuccess(res);
    final list = (body['clawList'] as List?) ?? const [];
    return list.map((e) => ClawDevice.fromJson((e as Map).cast())).toList();
  }

  Future<List<Map<String, dynamic>>> getMessages(
    int userId,
    String clawId,
  ) async {
    final res = await _dio.get(
      '${AppConfig.apiPrefix}/messages',
      queryParameters: {'userId': userId, 'clawId': clawId},
      options: _authOptions(),
    );
    final body = await _requireSuccess(res);
    final messages = (body['messages'] as List?) ?? const [];
    return messages.map((e) => (e as Map).cast<String, dynamic>()).toList();
  }

  Future<void> deleteMessages(int userId, String clawId) async {
    final res = await _dio.delete(
      '${AppConfig.apiPrefix}/messages',
      queryParameters: {'userId': userId, 'clawId': clawId},
      options: _authOptions(),
    );
    await _requireSuccess(res);
  }

  Future<void> sendMessage({
    required int userId,
    required String messageId,
    required String clawId,
    required String content,
    required List<Map<String, dynamic>> attachments,
    String? tabId,
  }) async {
    final payload = {
      'userId': userId.toString(),
      'messageId': messageId,
      'clawId': clawId,
      if (tabId != null && tabId.isNotEmpty) 'tabId': tabId,
      if (content.isNotEmpty) 'content': content,
      if (attachments.isNotEmpty) 'attachments': attachments,
    };

    final res = await _dio.post(
      '${AppConfig.apiPrefix}/claw/send',
      data: payload,
      options: _authOptions(),
    );
    await _requireSuccess(res);
  }

  Future<List<UploadedServerFile>> uploadFiles({
    required int userId,
    required String clawId,
    required List<UploadFileInput> files,
  }) async {
    final formData = FormData.fromMap({
      'userId': userId.toString(),
      'clawId': clawId,
      'files': files
          .map(
            (f) => MultipartFile.fromBytes(
              f.bytes,
              filename: f.name,
              contentType: MediaType.parse(f.mimeType),
            ),
          )
          .toList(),
    });

    final res = await _dio.post(
      '${AppConfig.apiPrefix}/file/upload',
      data: formData,
      options: _authOptions(),
    );

    final body = await _requireSuccess(res);
    final list = (body['files'] as List?) ?? const [];
    return list
        .map(
          (e) =>
              UploadedServerFile.fromJson((e as Map).cast<String, dynamic>()),
        )
        .toList();
  }

  /// 阿里云百炼语音转写：上传本地录音，一次请求返回全文。
  Future<String> bailianSpeechTranscribe(
    String filePath, {
    int? durationMs,
  }) async {
    final form = FormData.fromMap({
      'file': await MultipartFile.fromFile(filePath),
      if (durationMs != null) 'durationMs': durationMs.toString(),
    });
    final res = await _dio.post(
      '${AppConfig.apiPrefix}/speech/bailian/transcribe',
      data: form,
      options: Options(
        headers: {
          if (_token != null && _token!.isNotEmpty)
            'Authorization': 'Bearer $_token',
        },
        sendTimeout: const Duration(minutes: 2),
        receiveTimeout: const Duration(minutes: 2),
      ),
    );
    final data = res.data;
    if (data is Map && data['success'] == true && data['text'] != null) {
      return data['text'].toString();
    }
    final msg = data is Map ? data['message']?.toString() : null;
    throw Exception(msg ?? '语音识别失败');
  }
}
