import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:file_picker/file_picker.dart';
import 'package:mime/mime.dart';

import '../core/api_client.dart';
import '../core/text_extract.dart';
import '../core/ws_client.dart';
import '../models/openhsd_models.dart';
import './auth_controller.dart';

class ChatController extends ChangeNotifier {
  final AuthController auth;

  ChatController({required this.auth}) {
    api = ApiClient(token: auth.token);
    ws = WsAiClient(
      onChunk: _handleWsChunk,
      onFinal: _handleWsFinal,
      onStatus: (connected) {
        wsConnected = connected;
        notifyListeners();
      },
    );
  }

  late final ApiClient api;
  late final WsAiClient ws;

  bool wsConnected = false;
  bool loadingDevices = false;
  bool loadingHistory = false;
  String? lastDeviceRefreshError;
  int selectedClawIndex = 0;
  String currentSession = 'main'; // 'main' | 'session2'

  List<ClawDevice> clawList = [];
  List<ChatMessage> messages = [];

  // Composer state
  String inputText = '';
  List<ChatAttachment> attachments = [];

  // Used for streaming updates.
  final Map<String, String> pendingContent = {};
  int _msgCounter = 0;
  String _nextMessageId() => 'msg_${DateTime.now().millisecondsSinceEpoch}_${_msgCounter++}';

  int get userId => auth.user?.userId ?? 0;

  Future<void> init() async {
    if (!auth.isLoggedIn || auth.user == null) return;

    api.updateToken(auth.token);
    await ws.connect(userId);

    await fetchClawStatus();

    if (clawList.isNotEmpty) {
      selectedClawIndex = 0;
      await onClawChanged(clawList[0].clawId);
    }
  }

  ClawDevice? get selectedClaw => clawList.isEmpty ? null : clawList[selectedClawIndex];

  Future<bool> fetchClawStatus() async {
    if (!auth.isLoggedIn || userId <= 0) {
      lastDeviceRefreshError = '登录状态失效，请重新登录';
      notifyListeners();
      return false;
    }

    loadingDevices = true;
    lastDeviceRefreshError = null;
    notifyListeners();
    try {
      final list = await api.getClawStatus(userId);
      clawList = list;
      return true;
    } catch (e) {
      // Keep old list and expose error to UI.
      lastDeviceRefreshError = e.toString();
      return false;
    } finally {
      loadingDevices = false;
      notifyListeners();
    }
  }

  Future<void> onClawChanged(String clawId) async {
    if (currentSession != 'main') {
      messages = [];
      notifyListeners();
      return;
    }

    await loadHistory(clawId);
  }

  Future<void> loadHistory(String clawId) async {
    loadingHistory = true;
    notifyListeners();
    try {
      final history = await api.getMessages(userId, clawId);
      final loaded = history.map((m) {
        final role = m['role']?.toString() ?? '';
        final contentRaw = m['content'] ?? '';
        final content = extractText(contentRaw);
        return ChatMessage(
          messageId: m['messageId']?.toString() ?? '',
          role: role,
          content: content,
          loading: false,
          attachments: const [],
        );
      }).toList();
      final lastNewIdx = loaded.lastIndexWhere(
        (m) => m.role == 'user' && m.content.trim().toLowerCase() == '/new',
      );
      messages = lastNewIdx >= 0 ? loaded.sublist(lastNewIdx + 1) : loaded;
    } finally {
      loadingHistory = false;
      notifyListeners();
    }
  }

  Future<void> clearHistoryForCurrentClaw() async {
    final claw = selectedClaw;
    if (claw == null) return;
    try {
      await api.deleteMessages(userId, claw.clawId);
      messages = [];
      notifyListeners();
    } catch (_) {}
  }

  void clearMessagesLocal() {
    messages = [];
    notifyListeners();
  }

  Future<void> startNewConversation() async {
    if (currentSession != 'main') {
      clearMessagesLocal();
      return;
    }
    if (selectedClaw == null) {
      clearMessagesLocal();
      return;
    }

    // Keep the new conversation command clean and deterministic.
    messages = [];
    inputText = '/new';
    attachments = [];
    notifyListeners();
    await send();
  }

  void setSession(String session) {
    if (currentSession == session) return;
    currentSession = session;
    if (currentSession != 'main') {
      messages = [];
    } else {
      final claw = selectedClaw;
      if (claw != null) loadHistory(claw.clawId);
    }
    notifyListeners();
  }

  void setComposerInput(String v) {
    inputText = v;
    notifyListeners();
  }

  void addImageAttachment({
    required String name,
    required String dataUri,
    required int sizeBytes,
  }) {
    final uid = '${DateTime.now().millisecondsSinceEpoch}-${_msgCounter++}';
    attachments = [
      ...attachments,
      ChatAttachment(
        uid: uid,
        name: name,
        isImage: true,
        dataUri: dataUri,
        objectKey: null,
        mimeType: null,
        sizeBytes: sizeBytes,
      )
    ];
    notifyListeners();
  }

  void addDocumentAttachment({
    required String name,
    required String objectKey,
    required String mimeType,
    required int sizeBytes,
  }) {
    final uid = '${DateTime.now().millisecondsSinceEpoch}-${_msgCounter++}';
    attachments = [
      ...attachments,
      ChatAttachment(
        uid: uid,
        name: name,
        isImage: false,
        dataUri: null,
        objectKey: objectKey,
        mimeType: mimeType,
        sizeBytes: sizeBytes,
      )
    ];
    notifyListeners();
  }

  void removeAttachment(String uid) {
    attachments = attachments.where((a) => a.uid != uid).toList();
    notifyListeners();
  }

  Future<void> send() async {
    final claw = selectedClaw;
    if (claw == null) return;

    final text = inputText.trim();
    if (text.isEmpty && attachments.isEmpty) return;

    // Ensure WS is connected (may have been dropped).
    if (!wsConnected) {
      await ws.connect(userId);
    }

    final messageId = _nextMessageId();
    pendingContent[messageId] = '';

    // Push user message + assistant placeholder.
    final userAttachments = attachments;
    messages = [
      ...messages,
      ChatMessage(
        messageId: messageId,
        role: 'user',
        content: text,
        loading: false,
        attachments: userAttachments,
      ),
      ChatMessage(
        messageId: messageId,
        role: 'assistant',
        content: '',
        loading: true,
        attachments: const [],
      ),
    ];

    inputText = '';
    attachments = [];
    notifyListeners();

    // Build attachments payload:
    // - images: { type: 'image', base64: dataUri, name }
    // - documents: { objectKey, name, type: mimeType, size }
    final payloadAttachments = <Map<String, dynamic>>[];
    for (final a in userAttachments) {
      if (a.isImage) {
        final mimeType = _extractMimeTypeFromDataUri(a.dataUri ?? '') ?? 'image/png';
        payloadAttachments.add({
          // For images we send the real MIME type so backend/plugin can classify correctly.
          'type': mimeType,
          'base64': a.dataUri, // data:image/...;base64,xxxx
          'name': a.name,
        });
      } else {
        payloadAttachments.add({
          'objectKey': a.objectKey,
          'name': a.name,
          'type': a.mimeType ?? 'application/octet-stream',
          if (a.sizeBytes != null) 'size': a.sizeBytes,
        });
      }
    }

    try {
      await api.sendMessage(
        userId: userId,
        messageId: messageId,
        clawId: claw.clawId,
        content: text,
        attachments: payloadAttachments,
      );
      // WS will stream the real assistant message.
    } catch (e) {
      // If HTTP send fails, mark assistant as finished with error.
      final idx = messages.indexWhere(
        (m) => m.messageId == messageId && m.role == 'assistant',
      );
      if (idx >= 0) {
        messages[idx] = messages[idx].copyWith(
          content: '发送失败：${e.toString()}',
          loading: false,
        );
      }
      pendingContent.remove(messageId);
      notifyListeners();
    }
  }

  String? _extractMimeTypeFromDataUri(String dataUri) {
    // data:image/png;base64,xxxxx -> image/png
    if (!dataUri.startsWith('data:')) return null;
    final commaIdx = dataUri.indexOf(',');
    if (commaIdx <= 0) return null;
    final header = dataUri.substring(0, commaIdx); // data:image/png;base64
    final semiIdx = header.indexOf(';');
    if (semiIdx <= 5) return null;
    return header.substring(5, semiIdx);
  }

  Future<void> addPickedFiles(List<PlatformFile> picked) async {
    if (selectedClaw == null) {
      throw Exception('请先选择设备（设备用于文件上传与附件解析）');
    }

    final clawId = selectedClaw!.clawId;
    final maxSize = 50 * 1024 * 1024; // 50MB

    final imageExts = <String>{
      'jpg',
      'jpeg',
      'png',
      'gif',
      'webp',
      'bmp',
      'svg',
    };

    bool isImageName(String name) {
      final ext = name.split('.').last.toLowerCase();
      return imageExts.contains(ext);
    }

    String lookupMime(String name) {
      final lower = name.toLowerCase();
      if (lower.endsWith('.png')) return 'image/png';
      if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg';
      if (lower.endsWith('.gif')) return 'image/gif';
      if (lower.endsWith('.webp')) return 'image/webp';
      if (lower.endsWith('.bmp')) return 'image/bmp';
      if (lower.endsWith('.svg')) return 'image/svg+xml';

      if (lower.endsWith('.pdf')) return 'application/pdf';
      if (lower.endsWith('.doc')) return 'application/msword';
      if (lower.endsWith('.docx')) {
        return 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
      }
      if (lower.endsWith('.xls')) return 'application/vnd.ms-excel';
      if (lower.endsWith('.xlsx')) {
        return 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
      }
      if (lower.endsWith('.ppt')) {
        return 'application/vnd.ms-powerpoint';
      }
      if (lower.endsWith('.pptx')) {
        return 'application/vnd.openxmlformats-officedocument.presentationml.presentation';
      }
      if (lower.endsWith('.txt')) return 'text/plain';
      if (lower.endsWith('.md')) return 'text/markdown';
      if (lower.endsWith('.csv')) return 'text/csv';
      if (lower.endsWith('.json')) return 'application/json';
      if (lower.endsWith('.zip')) return 'application/zip';
      if (lower.endsWith('.gz')) return 'application/gzip';

      // Fallback to mime lookup.
      return lookupMimeType(name) ?? 'application/octet-stream';
    }

    final imagePicked = <PlatformFile>[];
    final docPicked = <PlatformFile>[];

    for (final f in picked) {
      final fileName = f.name;
      final size = f.size;
      if (size > maxSize) continue;
      if (isImageName(fileName)) {
        imagePicked.add(f);
      } else {
        docPicked.add(f);
      }
    }

    // 1) Images -> local base64 attachments (no /api/file/upload needed)
    for (final f in imagePicked) {
      final bytes = f.bytes;
      if (bytes == null) continue;
      final mimeType = lookupMime(f.name);
      final dataUri = 'data:$mimeType;base64,${base64Encode(bytes)}';
      addImageAttachment(
        name: f.name,
        dataUri: dataUri,
        sizeBytes: f.size,
      );
    }

    // 2) Docs -> upload -> objectKey attachments
    if (docPicked.isNotEmpty) {
      final uploadInputs = <UploadFileInput>[];
      for (final f in docPicked) {
        final bytes = f.bytes;
        if (bytes == null) continue;
        uploadInputs.add(
          UploadFileInput(
            name: f.name,
            bytes: bytes,
            mimeType: lookupMime(f.name),
            sizeBytes: f.size,
          ),
        );
      }

      if (uploadInputs.isNotEmpty) {
        final uploaded = await api.uploadFiles(
          userId: userId,
          clawId: clawId,
          files: uploadInputs,
        );

        // Assume response order matches upload order.
        for (final uf in uploaded) {
          addDocumentAttachment(
            name: uf.name,
            objectKey: uf.objectKey,
            mimeType: uf.mimeType,
            sizeBytes: uf.sizeBytes,
          );
        }
      }
    }
  }

  void _handleWsChunk(String messageId, String chunk, int seq) {
    final text = extractText(chunk);
    final prev = pendingContent[messageId] ?? '';
    final next = prev + text;
    pendingContent[messageId] = next;

    final idx = messages.indexWhere(
      (m) => m.messageId == messageId && m.role == 'assistant',
    );
    if (idx >= 0) {
      messages[idx] = messages[idx].copyWith(content: next, loading: true);
      notifyListeners();
    }
  }

  void _handleWsFinal(
    String messageId,
    String status,
    String result,
    dynamic attachments,
  ) {
    final text = extractText(result);
    final fallback = pendingContent[messageId] ?? '';
    final finalText = text.isNotEmpty ? text : fallback;

    final idx = messages.indexWhere(
      (m) => m.messageId == messageId && m.role == 'assistant',
    );
    if (idx >= 0) {
      final isError = status == 'error';
      messages[idx] = messages[idx].copyWith(
        content: isError ? (finalText.isNotEmpty ? finalText : '任务出错') : finalText,
        loading: false,
      );
      notifyListeners();
    }

    pendingContent.remove(messageId);
  }

  @override
  void dispose() {
    ws.disconnect();
    super.dispose();
  }
}

