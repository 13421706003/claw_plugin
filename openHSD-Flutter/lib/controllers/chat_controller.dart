import 'dart:async';
import 'dart:convert';
import 'dart:math';

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
      onFilePush: _handleWsFilePush,
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
  final Map<String, Timer> _responseTimeouts = {};
  static const Duration _responseTimeout = Duration(minutes: 1);
  int _msgCounter = 0;
  String _nextMessageId() =>
      'msg_${DateTime.now().millisecondsSinceEpoch}_${_msgCounter++}';
  final String _tabId =
      'flutter_${DateTime.now().millisecondsSinceEpoch}_${Random().nextInt(1 << 20)}';

  int get userId => auth.user?.userId ?? 0;

  Future<void> init() async {
    if (!auth.isLoggedIn || auth.user == null) return;

    api.updateToken(auth.token);
    await ws.connect(userId, tabId: _tabId);

    await fetchClawStatus();

    if (clawList.isNotEmpty) {
      selectedClawIndex = 0;
      await onClawChanged(clawList[0].clawId);
    }
  }

  /// Debug helper: hot reload does not rerun initState, so force WS refresh.
  Future<void> reconnectWs() async {
    if (!auth.isLoggedIn || auth.user == null) return;
    ws.disconnect();
    await ws.connect(userId, tabId: _tabId);
  }

  ClawDevice? get selectedClaw =>
      clawList.isEmpty ? null : clawList[selectedClawIndex];

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
        final parsedAttachments = _parseHistoryAttachments(m['attachments']);
        return ChatMessage(
          messageId: m['messageId']?.toString() ?? '',
          role: role,
          content: content,
          loading: false,
          attachments: parsedAttachments,
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
    String? objectKey,
    String? mimeType,
    String? url,
  }) {
    final uid = '${DateTime.now().millisecondsSinceEpoch}-${_msgCounter++}';
    attachments = [
      ...attachments,
      ChatAttachment(
        uid: uid,
        name: name,
        isImage: true,
        dataUri: dataUri,
        objectKey: objectKey,
        url: url,
        mimeType: mimeType,
        sizeBytes: sizeBytes,
      ),
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
      ),
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
      await ws.connect(userId, tabId: _tabId);
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
    _startOrResetResponseTimeout(messageId);

    // Build attachments payload:
    // - images: { type: 'image', base64: dataUri, name }
    // - documents: { objectKey, name, type: mimeType, size }
    final payloadAttachments = <Map<String, dynamic>>[];
    for (final a in userAttachments) {
      if (a.isImage) {
        final mimeType =
            _extractMimeTypeFromDataUri(a.dataUri ?? '') ?? 'image/png';
        if (a.objectKey != null && a.objectKey!.isNotEmpty) {
          payloadAttachments.add({
            // Match Web: images send objectKey + base64 together.
            'objectKey': a.objectKey,
            'type': a.mimeType ?? mimeType,
            'base64': a.dataUri, // data:image/...;base64,xxxx
            'name': a.name,
            if (a.sizeBytes != null) 'size': a.sizeBytes,
          });
        } else {
          payloadAttachments.add({
            'type': mimeType,
            'base64': a.dataUri, // fallback for pasted legacy image
            'name': a.name,
          });
        }
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
        tabId: _tabId,
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
      _clearResponseTimeout(messageId);
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
      if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) {
        return 'image/jpeg';
      }
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

    // 1) Images -> upload to server (get objectKey) + keep local base64 preview
    for (final f in imagePicked) {
      final bytes = f.bytes;
      if (bytes == null) continue;
      final mimeType = lookupMime(f.name);
      final dataUri = 'data:$mimeType;base64,${base64Encode(bytes)}';
      final uploaded = await api.uploadFiles(
        userId: userId,
        clawId: clawId,
        files: [
          UploadFileInput(
            name: f.name,
            bytes: bytes,
            mimeType: mimeType,
            sizeBytes: f.size,
          ),
        ],
      );
      final serverFile = uploaded.isNotEmpty ? uploaded.first : null;
      addImageAttachment(name: f.name, dataUri: dataUri, sizeBytes: f.size);
      if (serverFile != null && serverFile.objectKey.isNotEmpty) {
        // Replace latest image attachment with objectKey-aware one.
        removeAttachment(attachments.last.uid);
        addImageAttachment(
          name: f.name,
          dataUri: dataUri,
          sizeBytes: f.size,
          objectKey: serverFile.objectKey,
          mimeType: serverFile.mimeType,
          url: serverFile.url,
        );
      }
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
    _startOrResetResponseTimeout(messageId);
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
    _clearResponseTimeout(messageId);
    final text = extractText(result);
    final fallback = pendingContent[messageId] ?? '';
    final finalText = text.isNotEmpty ? text : fallback;

    final idx = messages.indexWhere(
      (m) => m.messageId == messageId && m.role == 'assistant',
    );
    final resolvedAttachments = _parseRealtimeAttachments(attachments);
    if (idx >= 0) {
      final isError = status == 'error';
      messages[idx] = messages[idx].copyWith(
        content: isError
            ? (finalText.isNotEmpty ? finalText : '任务出错')
            : finalText,
        loading: false,
        attachments: resolvedAttachments.isEmpty
            ? messages[idx].attachments
            : resolvedAttachments,
      );
      notifyListeners();
    }

    pendingContent.remove(messageId);
  }

  void _startOrResetResponseTimeout(String messageId) {
    _clearResponseTimeout(messageId);
    _responseTimeouts[messageId] = Timer(_responseTimeout, () async {
      final idx = messages.indexWhere(
        (m) => m.messageId == messageId && m.role == 'assistant',
      );
      if (idx < 0) return;
      if (!messages[idx].loading) return;

      final existing = messages[idx].content.trim();
      messages[idx] = messages[idx].copyWith(
        content: existing.isNotEmpty ? existing : '响应超时，已自动刷新连接，请重试',
        loading: false,
      );
      pendingContent.remove(messageId);
      notifyListeners();

      // Auto-refresh connection when spinner is stuck for too long.
      await reconnectWs();

      final claw = selectedClaw;
      if (claw != null && currentSession == 'main') {
        try {
          await loadHistory(claw.clawId);
        } catch (_) {}
      }
    });
  }

  void _clearResponseTimeout(String messageId) {
    final timer = _responseTimeouts.remove(messageId);
    timer?.cancel();
  }

  void _handleWsFilePush(
    String messageId,
    String clawId,
    String fileUrl,
    String fileName,
    String fileType,
    int? fileSize,
  ) {
    if (fileUrl.trim().isEmpty) return;
    final lowerType = fileType.toLowerCase();
    final lowerName = fileName.toLowerCase();
    final isImage =
        lowerType.startsWith('image/') ||
        lowerName.endsWith('.png') ||
        lowerName.endsWith('.jpg') ||
        lowerName.endsWith('.jpeg') ||
        lowerName.endsWith('.gif') ||
        lowerName.endsWith('.webp') ||
        lowerName.endsWith('.bmp') ||
        lowerName.endsWith('.svg');

    final pushed = ChatAttachment(
      uid: 'push_${DateTime.now().millisecondsSinceEpoch}_${_msgCounter++}',
      name: fileName,
      isImage: isImage,
      dataUri: isImage ? fileUrl : null,
      objectKey: null,
      url: fileUrl,
      mimeType: fileType,
      sizeBytes: fileSize,
    );

    messages = [
      ...messages,
      ChatMessage(
        messageId: messageId.isNotEmpty
            ? messageId
            : 'file_${DateTime.now().millisecondsSinceEpoch}',
        role: 'assistant',
        content: clawId.isNotEmpty ? '设备 $clawId 推送了文件' : '收到文件推送',
        loading: false,
        attachments: [pushed],
      ),
    ];
    notifyListeners();
  }

  List<ChatAttachment> _parseHistoryAttachments(dynamic raw) {
    if (raw == null) return const [];
    dynamic decoded = raw;
    if (raw is String && raw.trim().isNotEmpty) {
      try {
        decoded = jsonDecode(raw);
      } catch (_) {
        return const [];
      }
    }
    if (decoded is! List) return const [];
    return _parseRealtimeAttachments(decoded);
  }

  List<ChatAttachment> _parseRealtimeAttachments(dynamic raw) {
    if (raw is! List) return const [];
    final out = <ChatAttachment>[];
    for (var i = 0; i < raw.length; i++) {
      final item = raw[i];
      if (item is! Map) continue;
      final map = item.cast<dynamic, dynamic>();
      final name = (map['name'] ?? 'file').toString();
      final url = (map['url'] ?? '').toString();
      final objectKey = map['objectKey']?.toString();
      final mimeType =
          (map['type'] ?? map['mimeType'] ?? 'application/octet-stream')
              .toString();
      final dataUri = (map['base64'] ?? '').toString();
      final isImage =
          mimeType.startsWith('image/') ||
          name.toLowerCase().endsWith('.png') ||
          name.toLowerCase().endsWith('.jpg') ||
          name.toLowerCase().endsWith('.jpeg') ||
          name.toLowerCase().endsWith('.gif') ||
          name.toLowerCase().endsWith('.webp') ||
          name.toLowerCase().endsWith('.bmp') ||
          name.toLowerCase().endsWith('.svg');
      final size = (map['size'] as num?)?.toInt();

      out.add(
        ChatAttachment(
          uid: '${DateTime.now().millisecondsSinceEpoch}_$i',
          name: name,
          isImage: isImage,
          dataUri: dataUri.isNotEmpty
              ? dataUri
              : (isImage && url.isNotEmpty ? url : null),
          objectKey: objectKey,
          url: url.isNotEmpty ? url : null,
          mimeType: mimeType,
          sizeBytes: size,
        ),
      );
    }
    return out;
  }

  @override
  void dispose() {
    for (final t in _responseTimeouts.values) {
      t.cancel();
    }
    _responseTimeouts.clear();
    ws.disconnect();
    super.dispose();
  }
}
