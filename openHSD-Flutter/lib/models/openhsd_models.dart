import 'dart:typed_data';

class OpenHsdUser {
  final int userId;
  final String username;
  final String token;

  const OpenHsdUser({
    required this.userId,
    required this.username,
    required this.token,
  });

  Map<String, dynamic> toJson() => {
    'userId': userId,
    'username': username,
    'token': token,
  };

  static OpenHsdUser fromJson(Map<String, dynamic> json) {
    final rawUserId = json['userId'];
    final userId = rawUserId is int
        ? rawUserId
        : (rawUserId is String ? int.tryParse(rawUserId) ?? 0 : 0);
    return OpenHsdUser(
      userId: userId,
      username: (json['username'] ?? '').toString(),
      token: (json['token'] ?? '').toString(),
    );
  }
}

class ClawDevice {
  final String clawId;
  final String openClawDeviceId;
  final int lastHeartbeat;

  const ClawDevice({
    required this.clawId,
    required this.openClawDeviceId,
    required this.lastHeartbeat,
  });

  static ClawDevice fromJson(Map<String, dynamic> json) {
    return ClawDevice(
      clawId: (json['clawId'] ?? '').toString(),
      openClawDeviceId: (json['openClawDeviceId'] ?? '').toString(),
      lastHeartbeat: (json['lastHeartbeat'] ?? 0) is int
          ? (json['lastHeartbeat'] ?? 0) as int
          : int.tryParse((json['lastHeartbeat'] ?? '0').toString()) ?? 0,
    );
  }
}

class ChatAttachment {
  final String uid;
  final String name;
  final bool isImage;
  // Images: data:image/png;base64,...
  final String? dataUri;

  // Documents (or non-image attachments): pre-upload objectKey.
  final String? objectKey;

  // Public URL resolved by backend (history / push / ws final attachment rendering).
  final String? url;

  // Documents (or non-image attachments): mimeType, used by plugin extractor.
  final String? mimeType;

  final int? sizeBytes;

  const ChatAttachment({
    required this.uid,
    required this.name,
    required this.isImage,
    this.dataUri,
    this.objectKey,
    this.url,
    this.mimeType,
    this.sizeBytes,
  });
}

class ChatMessage {
  final String messageId;
  final String role; // 'user' | 'assistant'
  final String content; // extracted text or markdown
  final bool loading;
  final List<ChatAttachment> attachments;

  const ChatMessage({
    required this.messageId,
    required this.role,
    required this.content,
    required this.loading,
    this.attachments = const [],
  });

  ChatMessage copyWith({
    String? content,
    bool? loading,
    List<ChatAttachment>? attachments,
  }) {
    return ChatMessage(
      messageId: messageId,
      role: role,
      content: content ?? this.content,
      loading: loading ?? this.loading,
      attachments: attachments ?? this.attachments,
    );
  }
}

class UploadFileInput {
  final String name;
  final Uint8List bytes;
  final String mimeType;
  final int sizeBytes;

  const UploadFileInput({
    required this.name,
    required this.bytes,
    required this.mimeType,
    required this.sizeBytes,
  });
}

class UploadedServerFile {
  final String objectKey;
  final String url;
  final String name;
  final String mimeType;
  final int sizeBytes;

  const UploadedServerFile({
    required this.objectKey,
    required this.url,
    required this.name,
    required this.mimeType,
    required this.sizeBytes,
  });

  static UploadedServerFile fromJson(Map<String, dynamic> json) {
    return UploadedServerFile(
      objectKey: json['objectKey']?.toString() ?? '',
      url: json['url']?.toString() ?? '',
      name: json['name']?.toString() ?? 'file',
      mimeType:
          json['type']?.toString() ??
          json['mimeType']?.toString() ??
          'application/octet-stream',
      sizeBytes: (json['size'] as num?)?.toInt() ?? 0,
    );
  }
}
