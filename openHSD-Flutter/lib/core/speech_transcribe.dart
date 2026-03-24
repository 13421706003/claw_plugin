import 'api_client.dart';

/// 经后端代理阿里云百炼 [实时语音识别](https://www.alibabacloud.com/help/zh/model-studio/real-time-speech-recognition)，单次请求返回全文。
Future<String> transcribeLocalAudioViaBackend(
  ApiClient api,
  String filePath, {
  int? durationMs,
}) async {
  return api.bailianSpeechTranscribe(filePath, durationMs: durationMs);
}
