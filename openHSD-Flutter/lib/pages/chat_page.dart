import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:image_gallery_saver_plus/image_gallery_saver_plus.dart';
import 'package:provider/provider.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter/services.dart';
import 'package:dio/dio.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:record/record.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:video_player/video_player.dart';

import '../controllers/auth_controller.dart';
import '../controllers/chat_controller.dart';
import '../core/app_config.dart';
import '../core/api_client.dart';
import '../core/speech_transcribe.dart';
import '../models/openhsd_models.dart';

part 'account_balance_page.dart';
part 'recharge_page.dart';
part 'profile_center_page.dart';

/// 与 openHSD-APP/styles/theme.less 对齐的聊天页 token
abstract final class _OhsdChatTheme {
  static const Color pageBg = Color(0xFFFFFFFF);
  static const Color bgPage = Color(0xFFFAFAFA);
  static const Color bgSurface = Color(0xFFFFFFFF);
  static const Color bgIos = Color(0xFFF2F2F7);
  static const Color borderHairline = Color(0x0F000000);
  static const Color borderLight = Color(0x1A000000);
  static const Color textPrimary = Color(0xE0000000);
  static const Color textSecondary = Color(0x8C000000);
  static const Color textTertiary = Color(0x73000000);
  static const Color textSubtle = Color(0x61000000);
  static const Color textPlaceholder = Color(0x40000000);
  static const Color primary = Color(0xFF1677FF);
  static const Color primarySoft = Color(0x591677FF);
  static const Color success = Color(0xFF52C41A);
  static const Color successMuted = Color(0x1A52C41A);
  static const Color successRing = Color(0x4052C41A);
  static const Color dangerMuted = Color(0x14FF4D4F);
  static const Color error = Color(0xFFFF4D4F);
  static const Color dangerDeep = Color(0xFFD9363E);
  static const Color toolIcon = Color(0x94000000);
  static const LinearGradient welcomeCardGrad = LinearGradient(
    begin: Alignment(-0.2, -1),
    end: Alignment(1, 1),
    colors: [Color(0xFFE5F4FF), Color(0xFFEFE7FF)],
  );
  static const String logoNetworkUrl =
      'https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*eco6RrQhxbMAAAAAAAAAAAAADgCCAQ/original';
  static const String welcomeIconNetworkUrl =
      'https://mdn.alipayobjects.com/huamei_iwk9zp/afts/img/A*s5sNRo5LjfQAAAAAAAAAAAAADgCCAQ/fmt.webp';

  // 用户面板（对齐 chat.vue .user-panel / .panel-*）
  static const Color overlayLight = Color(0x26000000);
  static const Color bgMuted = Color(0xFFF6F6F6);
  static const Color surfaceTintBlue = Color(0xFFF0F7FF);
  static const Color fill05 = Color(0x0D000000);
  static const Color panelDivider = Color(0xFFF0F0F0);
  static const Color headingStrong = Color(0xFF1A1A1A);
  static const Color captionHex = Color(0xFF999999);
  static const Color clawCardBg = Color(0xFFF8FFFE);
  static const Color clawCardBorder = Color(0xFFD9F7BE);
  static const Color dangerFaintBg = Color(0x0FFF4D4F);
  static const Color tokenBodyHex = Color(0xFF333333);
}

String _sanitizeFileName(String name) {
  var n = name.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_').trim();
  if (n.isEmpty) n = 'download';
  return n;
}

Future<File> _saveBytesToDownloads(String preferredName, List<int> bytes) async {
  final dir =
      await getDownloadsDirectory() ?? await getApplicationDocumentsDirectory();
  final base = _sanitizeFileName(preferredName);
  var path = '${dir.path}/$base';
  if (await File(path).exists()) {
    final dot = base.lastIndexOf('.');
    final ts = DateTime.now().millisecondsSinceEpoch;
    path = dot > 0
        ? '${dir.path}/${base.substring(0, dot)}_$ts${base.substring(dot)}'
        : '${dir.path}/${base}_$ts';
  }
  final file = File(path);
  await file.writeAsBytes(bytes);
  return file;
}

bool _isImageAttachment(ChatAttachment att) {
  final mt = (att.mimeType ?? '').toLowerCase();
  if (mt.startsWith('image/')) return true;
  final dataUri = (att.dataUri ?? '').toLowerCase();
  if (dataUri.startsWith('data:image/')) return true;
  final n = att.name.toLowerCase();
  return n.endsWith('.png') ||
      n.endsWith('.jpg') ||
      n.endsWith('.jpeg') ||
      n.endsWith('.gif') ||
      n.endsWith('.webp') ||
      n.endsWith('.bmp') ||
      n.endsWith('.heic') ||
      n.endsWith('.heif');
}

Future<bool> _saveImageToGallery(String preferredName, List<int> bytes) async {
  final base = _sanitizeFileName(preferredName);
  final dot = base.lastIndexOf('.');
  final nameWithoutExt = dot > 0 ? base.substring(0, dot) : base;
  final result = await ImageGallerySaverPlus.saveImage(
    Uint8List.fromList(bytes),
    quality: 100,
    name: nameWithoutExt,
  );
  if (result is Map) {
    final ok = result['isSuccess'] ?? result['success'];
    if (ok is bool) return ok;
    if (ok is num) return ok != 0;
  }
  return result != null;
}

bool _isVideoAttachment(ChatAttachment att) {
  final mt = (att.mimeType ?? '').toLowerCase();
  if (mt.startsWith('video/')) return true;
  final n = att.name.toLowerCase();
  return n.endsWith('.mp4') || n.endsWith('.mov') || n.endsWith('.m4v');
}

Future<void> _downloadChatAttachment(
  BuildContext context,
  ChatAttachment att,
  String authToken,
  {bool showSnackBar = true}
) async {
  try {
    late List<int> bytes;
    final dataUri = att.dataUri;
    if (dataUri != null && dataUri.startsWith('data:')) {
      final comma = dataUri.indexOf(',');
      if (comma < 0) throw Exception('无效的数据');
      bytes = base64Decode(dataUri.substring(comma + 1));
    } else {
      final url = att.url ??
          (dataUri != null &&
                  (dataUri.startsWith('http://') || dataUri.startsWith('https://'))
              ? dataUri
              : null);
      if (url == null || url.isEmpty) {
        if (showSnackBar && context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('没有可下载的文件地址')),
          );
        }
        return;
      }
      final dio = Dio();
      final res = await dio.get<List<int>>(
        url,
        options: Options(
          responseType: ResponseType.bytes,
          headers: authToken.isNotEmpty
              ? {'Authorization': 'Bearer $authToken'}
              : {},
        ),
      );
      final raw = res.data;
      if (raw == null || raw.isEmpty) throw Exception('下载内容为空');
      bytes = raw;
    }
    if (_isImageAttachment(att)) {
      final ok = await _saveImageToGallery(att.name, bytes);
      if (!ok) throw Exception('保存到相册失败');
      if (showSnackBar && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('图片已保存到相册')),
        );
      }
    } else {
      final file = await _saveBytesToDownloads(att.name, bytes);
      if (showSnackBar && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已保存：${file.path}')),
        );
      }
    }
  } catch (e) {
    if (showSnackBar && context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('保存失败：$e')),
      );
    }
    rethrow;
  }
}

Future<void> _previewVideoAttachment(
  BuildContext context,
  ChatAttachment att,
  String authToken,
) async {
  try {
    final url = att.url;
    if (url == null || url.trim().isEmpty) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('暂无视频地址，无法预览')),
        );
      }
      return;
    }
    final uri = Uri.tryParse(url);
    if (uri == null) throw Exception('无效的视频地址');
    if (!context.mounted) return;
    await showDialog<void>(
      context: context,
      barrierColor: Colors.black87,
      builder: (ctx) => _VideoPreviewDialog(
        att: att,
        networkUri: uri,
        authToken: authToken,
      ),
    );
  } catch (e) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('预览失败：$e')),
      );
    }
  }
}

class _VideoPreviewDialog extends StatefulWidget {
  final ChatAttachment att;
  final Uri networkUri;
  final String authToken;

  const _VideoPreviewDialog({
    required this.att,
    required this.networkUri,
    required this.authToken,
  });

  @override
  State<_VideoPreviewDialog> createState() => _VideoPreviewDialogState();
}

class _VideoPreviewDialogState extends State<_VideoPreviewDialog> {
  VideoPlayerController? _ctl;
  Future<void>? _init;

  @override
  void initState() {
    super.initState();
    _ctl = VideoPlayerController.networkUrl(
      widget.networkUri,
      httpHeaders: widget.authToken.isNotEmpty
          ? {'Authorization': 'Bearer ${widget.authToken}'}
          : const <String, String>{},
    );
    _init = _ctl!.initialize().then((_) => _ctl!.play());
  }

  @override
  void dispose() {
    _ctl?.dispose();
    super.dispose();
  }

  Future<void> _saveToDownloads() async {
    try {
      final auth = context.read<AuthController>();
      await _downloadChatAttachment(context, widget.att, auth.token);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('保存失败：$e')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final ctl = _ctl;
    return Dialog(
      backgroundColor: Colors.transparent,
      insetPadding: EdgeInsets.zero,
      child: Stack(
        fit: StackFit.expand,
        children: [
          Center(
            child: FutureBuilder<void>(
              future: _init,
              builder: (ctx, snap) {
                if (snap.connectionState != ConnectionState.done || ctl == null) {
                  return const CircularProgressIndicator(color: Colors.white70);
                }
                final ar = ctl.value.aspectRatio;
                return AspectRatio(
                  aspectRatio: ar <= 0 ? 16 / 9 : ar,
                  child: VideoPlayer(ctl),
                );
              },
            ),
          ),
          SafeArea(
            child: Align(
              alignment: Alignment.topCenter,
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    IconButton(
                      icon: const Icon(Icons.close, color: Colors.white, size: 28),
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                    Row(
                      children: [
                        IconButton(
                          icon: Icon(
                            (ctl?.value.isPlaying ?? false)
                                ? Icons.pause_circle_outline
                                : Icons.play_circle_outline,
                            color: Colors.white,
                            size: 30,
                          ),
                          onPressed: () {
                            final c = _ctl;
                            if (c == null) return;
                            setState(() {
                              c.value.isPlaying ? c.pause() : c.play();
                            });
                          },
                        ),
                        IconButton(
                          icon: const Icon(
                            Icons.download_outlined,
                            color: Colors.white,
                            size: 28,
                          ),
                          onPressed: _saveToDownloads,
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

Widget _buildFullScreenPreviewImage(String src) {
  if (src.isEmpty) {
    return const Center(
      child: Text('无法预览', style: TextStyle(color: Colors.white70)),
    );
  }
  if (src.startsWith('http://') || src.startsWith('https://')) {
    return Image.network(src, fit: BoxFit.contain);
  }
  if (src.startsWith('data:')) {
    try {
      final comma = src.indexOf(',');
      if (comma < 0) return const SizedBox.shrink();
      return Image.memory(
        base64Decode(src.substring(comma + 1)),
        fit: BoxFit.contain,
      );
    } catch (_) {
      return const Center(
        child: Text('无法解析图片', style: TextStyle(color: Colors.white70)),
      );
    }
  }
  return const SizedBox.shrink();
}

void _showImagePreview(BuildContext context, ChatAttachment att) {
  final src = att.dataUri ?? att.url ?? '';
  showDialog<void>(
    context: context,
    barrierColor: Colors.black87,
    builder: (ctx) {
      String? tip;
      Timer? hideTipTimer;
      return StatefulBuilder(
        builder: (ctx, setState) {
          void showTip(String message) {
            hideTipTimer?.cancel();
            setState(() => tip = message);
            hideTipTimer = Timer(const Duration(seconds: 2), () {
              if (!ctx.mounted) return;
              setState(() => tip = null);
            });
          }

          return Dialog(
            backgroundColor: Colors.transparent,
            insetPadding: EdgeInsets.zero,
            child: Stack(
              fit: StackFit.expand,
              children: [
                Center(
                  child: InteractiveViewer(
                    minScale: 0.5,
                    maxScale: 5,
                    child: _buildFullScreenPreviewImage(src),
                  ),
                ),
                SafeArea(
                  child: Align(
                    alignment: Alignment.topCenter,
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 4),
                      child: Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          IconButton(
                            icon: const Icon(Icons.close, color: Colors.white, size: 28),
                            onPressed: () {
                              hideTipTimer?.cancel();
                              Navigator.of(ctx).pop();
                            },
                          ),
                          IconButton(
                            icon: const Icon(
                              Icons.download_outlined,
                              color: Colors.white,
                              size: 28,
                            ),
                            onPressed: () async {
                              final auth = context.read<AuthController>();
                              try {
                                await _downloadChatAttachment(
                                  context,
                                  att,
                                  auth.token,
                                  showSnackBar: false,
                                );
                                showTip('图片已保存到相册');
                              } catch (e) {
                                showTip('保存失败：$e');
                              }
                            },
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                if (tip != null)
                  SafeArea(
                    child: Align(
                      alignment: Alignment.bottomCenter,
                      child: Padding(
                        padding: const EdgeInsets.only(bottom: 24),
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            color: const Color(0xCC111111),
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Padding(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 12,
                              vertical: 8,
                            ),
                            child: Text(
                              tip!,
                              style: const TextStyle(color: Colors.white),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          );
        },
      );
    },
  );
}

void _copyMessageContent(BuildContext context, String text) {
  final content = text.trim();
  if (content.isEmpty) return;
  ClipboardStatus.setClipboard(content);
  if (!context.mounted) return;
  ScaffoldMessenger.of(context).showSnackBar(
    const SnackBar(content: Text('已复制回复内容')),
  );
}

Future<void> _showDocumentActionsSheet(
  BuildContext context,
  ChatAttachment att,
  String authToken,
) async {
  final url = att.url;
  if (url == null || url.isEmpty) {
    if (context.mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('暂无文件地址，无法预览或保存')),
      );
    }
    return;
  }
  await showModalBottomSheet<void>(
    context: context,
    backgroundColor: _OhsdChatTheme.bgSurface,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(12)),
    ),
    builder: (ctx) {
      return SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            if (_isVideoAttachment(att))
              ListTile(
                leading: const Icon(Icons.play_circle_outline),
                title: const Text('预览视频'),
                onTap: () async {
                  Navigator.pop(ctx);
                  await _previewVideoAttachment(context, att, authToken);
                },
              ),
            ListTile(
              leading: const Icon(Icons.visibility_outlined),
              title: const Text('预览'),
              onTap: () async {
                Navigator.pop(ctx);
                final uri = Uri.tryParse(url);
                if (uri == null) return;
                final ok = await canLaunchUrl(uri);
                if (!context.mounted) return;
                if (ok) {
                  await launchUrl(uri, mode: LaunchMode.inAppWebView);
                } else {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('无法打开该链接')),
                  );
                }
              },
            ),
            ListTile(
              leading: const Icon(Icons.download_outlined),
              title: const Text('保存到手机'),
              onTap: () async {
                Navigator.pop(ctx);
                await _downloadChatAttachment(context, att, authToken);
              },
            ),
          ],
        ),
      );
    },
  );
}

class ChatPage extends StatefulWidget {
  const ChatPage({super.key});

  @override
  State<ChatPage> createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage> {
  final GlobalKey<ScaffoldState> _scaffoldKey = GlobalKey<ScaffoldState>();
  late final ChatController chat;
  final _scrollCtl = ScrollController();
  final TextEditingController _inputCtl = TextEditingController();
  final FocusNode _inputFocus = FocusNode();
  bool _inputFocused = false;
  bool _voiceUiEnabled = false;
  bool _voiceMode = false;
  final AudioRecorder _audioRecorder = AudioRecorder();
  bool _voiceRecording = false;
  String? _activeRecordPath;
  DateTime? _voiceRecordStartedAt;

  /// 低于此时长的录音不送识别，避免误触与无效请求
  static const Duration _minVoiceRecordDuration = Duration(milliseconds: 800);

  static const _sessionOptions = <({String value, String label})>[
    (value: 'main', label: '主会话'),
    (value: 'session2', label: '会话 2'),
  ];

  Future<void> _sendMessage() async {
    // 与输入框状态对齐（避免偶发未触发 listener 时 inputText 滞后）
    chat.setComposerInput(_inputCtl.text);

    if (chat.selectedClaw == null) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('暂无可用设备：请等待在线设备出现，或点击工具栏「刷新」后再试')),
      );
      return;
    }

    final text = chat.inputText.trim();
    if (text.isEmpty && chat.attachments.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('请输入消息或添加附件后再发送')));
      return;
    }

    await chat.send();
    if (mounted) _inputCtl.clear();
  }

  @override
  void initState() {
    super.initState();
    final auth = context.read<AuthController>();
    chat = ChatController(auth: auth);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      await chat.init();
    });
    _inputCtl.addListener(() {
      chat.setComposerInput(_inputCtl.text);
    });
    _inputFocus.addListener(() {
      final f = _inputFocus.hasFocus;
      if (f != _inputFocused) setState(() => _inputFocused = f);
    });
  }

  @override
  void dispose() {
    _audioRecorder.dispose();
    _inputFocus.dispose();
    _inputCtl.dispose();
    _scrollCtl.dispose();
    chat.dispose();
    super.dispose();
  }

  @override
  void reassemble() {
    super.reassemble();
    if (kDebugMode) {
      unawaited(chat.reconnectWs());
    }
  }

  Future<void> _onVoiceLongPressStart() async {
    final status = await Permission.microphone.request();
    if (!status.isGranted) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('需要麦克风权限才能录音')));
      }
      return;
    }
    if (!await _audioRecorder.hasPermission()) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('无法使用麦克风')));
      }
      return;
    }
    try {
      final dir = await getTemporaryDirectory();
      final path =
          '${dir.path}/ohsd_asr_${DateTime.now().millisecondsSinceEpoch}.m4a';
      await _audioRecorder.start(
        const RecordConfig(encoder: AudioEncoder.aacLc),
        path: path,
      );
      if (mounted) {
        setState(() {
          _voiceRecording = true;
          _activeRecordPath = path;
          _voiceRecordStartedAt = DateTime.now();
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('无法开始录音：$e')));
      }
    }
  }

  Future<void> _onVoiceLongPressEnd() async {
    if (!_voiceRecording) return;
    setState(() => _voiceRecording = false);
    final startedAt = _voiceRecordStartedAt;
    _voiceRecordStartedAt = null;
    String? path;
    try {
      path = await _audioRecorder.stop();
    } catch (_) {}
    path ??= _activeRecordPath;
    _activeRecordPath = null;
    if (!mounted) return;

    if (startedAt != null) {
      final elapsed = DateTime.now().difference(startedAt);
      if (elapsed < _minVoiceRecordDuration) {
        if (path != null) {
          try {
            await File(path).delete();
          } catch (_) {}
        }
        if (!mounted) return;
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('录制时间过短，请长按说话')));
        return;
      }
    }

    if (path == null) return;

    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('正在识别语音…'), duration: Duration(seconds: 90)),
    );
    try {
      final text = await transcribeLocalAudioViaBackend(chat.api, path);
      if (!mounted) return;
      ScaffoldMessenger.of(context).clearSnackBars();
      final t = text.length > 500 ? text.substring(0, 500) : text;
      _inputCtl.text = t;
      chat.setComposerInput(t);
      setState(() {
        _voiceMode = false;
        _voiceUiEnabled = false;
      });
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('识别完成，已填入输入框')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).clearSnackBars();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    }
  }

  Future<void> _onVoiceLongPressCancel() async {
    if (!_voiceRecording) return;
    setState(() => _voiceRecording = false);
    _voiceRecordStartedAt = null;
    _activeRecordPath = null;
    try {
      await _audioRecorder.stop();
    } catch (_) {}
  }

  Future<void> _pickFiles() async {
    final result = await FilePicker.platform.pickFiles(
      type: FileType.custom,
      allowMultiple: true,
      withData: true,
      allowedExtensions: const [
        'jpg',
        'jpeg',
        'png',
        'gif',
        'webp',
        'bmp',
        'svg',
        'pdf',
        'doc',
        'docx',
        'xls',
        'xlsx',
        'md',
        'csv',
        'json',
        'zip',
        'gz',
      ],
    );
    if (result == null || result.files.isEmpty) return;

    try {
      await chat.addPickedFiles(result.files);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(e.toString().replaceFirst('Exception: ', ''))),
      );
    }
  }

  String _dataUriToBase64(String dataUri) {
    final idx = dataUri.indexOf(',');
    if (idx < 0) return '';
    return dataUri.substring(idx + 1);
  }

  ImageProvider _attachmentImageProvider(ChatAttachment att) {
    if (!att.isImage) return const AssetImage('assets/document.png');
    final data = att.dataUri ?? '';
    if (data.startsWith('http://') || data.startsWith('https://')) {
      return NetworkImage(data);
    }
    final b64 = _dataUriToBase64(data);
    if (b64.isEmpty) return const AssetImage('assets/document.png');
    try {
      return MemoryImage(base64Decode(b64));
    } catch (_) {
      return const AssetImage('assets/document.png');
    }
  }

  void _scrollToBottom() {
    if (!_scrollCtl.hasClients) return;
    final max = _scrollCtl.position.maxScrollExtent;
    _scrollCtl.animateTo(
      max,
      duration: const Duration(milliseconds: 150),
      curve: Curves.easeOut,
    );
  }

  @override
  Widget build(BuildContext context) {
    return ChangeNotifierProvider<ChatController>.value(
      value: chat,
      child: Consumer2<AuthController, ChatController>(
        builder: (context, auth, chat, _) {
          WidgetsBinding.instance.addPostFrameCallback((_) {
            _scrollToBottom();
          });

          final userInitial = (auth.user?.username.isNotEmpty ?? false)
              ? auth.user!.username[0].toUpperCase()
              : 'U';

          final sessionIdx = _sessionOptions.indexWhere(
            (e) => e.value == chat.currentSession,
          );
          final sessionIndex = sessionIdx < 0 ? 0 : sessionIdx;
          final sendLoading =
              chat.messages.isNotEmpty &&
              chat.messages.last.role == 'assistant' &&
              chat.messages.last.loading;
          final composerLocked = sendLoading;
          final sendActive =
              chat.inputText.trim().isNotEmpty || chat.attachments.isNotEmpty;

          return Scaffold(
            key: _scaffoldKey,
            backgroundColor: _OhsdChatTheme.pageBg,
            drawer: _buildNavigationDrawer(context, auth, chat),
            body: Column(
              children: [
                SafeArea(
                  bottom: false,
                  child: Container(
                    decoration: const BoxDecoration(
                      color: _OhsdChatTheme.bgSurface,
                      border: Border(
                        bottom: BorderSide(
                          color: _OhsdChatTheme.borderHairline,
                        ),
                      ),
                    ),
                    padding: const EdgeInsets.fromLTRB(14, 6, 14, 8),
                    child: Row(
                      children: [
                        Material(
                          color: Colors.transparent,
                          borderRadius: BorderRadius.circular(8),
                          child: InkWell(
                            onTap: () =>
                                _scaffoldKey.currentState?.openDrawer(),
                            borderRadius: BorderRadius.circular(8),
                            child: Padding(
                              padding: const EdgeInsets.symmetric(
                                vertical: 4,
                                horizontal: 2,
                              ),
                              child: Row(
                                children: [
                                  Container(
                                    width: 26,
                                    height: 26,
                                    decoration: BoxDecoration(
                                      color: _OhsdChatTheme.bgIos,
                                      borderRadius: BorderRadius.circular(7),
                                    ),
                                    alignment: Alignment.center,
                                    child: Image.network(
                                      _OhsdChatTheme.logoNetworkUrl,
                                      width: 15,
                                      height: 15,
                                      fit: BoxFit.contain,
                                      errorBuilder:
                                          (context, error, stackTrace) =>
                                              Padding(
                                                padding: const EdgeInsets.all(
                                                  4,
                                                ),
                                                child: SvgPicture.asset(
                                                  'assets/logo.svg',
                                                  fit: BoxFit.contain,
                                                ),
                                              ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  const Column(
                                    crossAxisAlignment:
                                        CrossAxisAlignment.start,
                                    children: [
                                      Text(
                                        '会宝',
                                        style: TextStyle(
                                          fontSize: 14,
                                          fontWeight: FontWeight.w700,
                                          color: _OhsdChatTheme.textPrimary,
                                          height: 1.2,
                                        ),
                                      ),
                                      Text(
                                        '网关聊天',
                                        style: TextStyle(
                                          fontSize: 11,
                                          color: _OhsdChatTheme.textSubtle,
                                          height: 1.2,
                                        ),
                                      ),
                                    ],
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 6,
                            vertical: 3,
                          ),
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(10),
                            color: chat.wsConnected
                                ? _OhsdChatTheme.successMuted
                                : _OhsdChatTheme.dangerMuted,
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Container(
                                width: 5,
                                height: 5,
                                decoration: BoxDecoration(
                                  shape: BoxShape.circle,
                                  color: chat.wsConnected
                                      ? _OhsdChatTheme.success
                                      : _OhsdChatTheme.error,
                                  boxShadow: chat.wsConnected
                                      ? [
                                          BoxShadow(
                                            color: _OhsdChatTheme.successRing,
                                            blurRadius: 0,
                                            spreadRadius: 1.5,
                                          ),
                                        ]
                                      : null,
                                ),
                              ),
                              const SizedBox(width: 3),
                              Text(
                                chat.wsConnected ? '已连接' : '离线',
                                style: const TextStyle(
                                  fontSize: 10,
                                  color: _OhsdChatTheme.textSecondary,
                                ),
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 6),
                        Material(
                          color: Colors.transparent,
                          child: InkWell(
                            onTap: () => _showUserPanel(auth, chat),
                            customBorder: const CircleBorder(),
                            child: Padding(
                              padding: const EdgeInsets.all(3),
                              child: Container(
                                width: 20,
                                height: 20,
                                decoration: const BoxDecoration(
                                  shape: BoxShape.circle,
                                  gradient: LinearGradient(
                                    begin: Alignment.topLeft,
                                    end: Alignment.bottomRight,
                                    colors: [
                                      _OhsdChatTheme.error,
                                      _OhsdChatTheme.dangerDeep,
                                    ],
                                  ),
                                ),
                                alignment: Alignment.center,
                                child: Text(
                                  userInitial,
                                  style: const TextStyle(
                                    fontSize: 10,
                                    fontWeight: FontWeight.w700,
                                    color: Colors.white,
                                  ),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                // 工具行：禁止在横向 SingleChildScrollView 的 Row 里使用 Spacer/Expanded（父级水平约束为无限宽会导致布局失败、页面空白）
                Container(
                  decoration: const BoxDecoration(
                    color: _OhsdChatTheme.bgPage,
                    border: Border(
                      bottom: BorderSide(color: _OhsdChatTheme.borderHairline),
                    ),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(12, 6, 12, 7),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.center,
                      children: [
                        Expanded(
                          child: chat.clawList.isEmpty
                              ? _ToolChip(
                                  minWidth: 0,
                                  maxWidth: double.infinity,
                                  borderStyle: BorderStyle.solid,
                                  background: const Color(0x08000000),
                                  child: const Text(
                                    '暂无在线设备',
                                    style: TextStyle(
                                      fontSize: 12,
                                      color: _OhsdChatTheme.textSecondary,
                                    ),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                )
                              : PopupMenuButton<int>(
                                  padding: EdgeInsets.zero,
                                  offset: const Offset(0, 34),
                                  color: _OhsdChatTheme.bgSurface,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                  onSelected: (i) async {
                                    chat.selectedClawIndex = i;
                                    await chat.onClawChanged(
                                      chat.clawList[i].clawId,
                                    );
                                  },
                                  itemBuilder: (ctx) => [
                                    for (
                                      var i = 0;
                                      i < chat.clawList.length;
                                      i++
                                    )
                                      PopupMenuItem(
                                        value: i,
                                        child: Text(chat.clawList[i].clawId),
                                      ),
                                  ],
                                  child: _ToolChip(
                                    minWidth: 0,
                                    maxWidth: double.infinity,
                                    child: Row(
                                      children: [
                                        Expanded(
                                          child: Text(
                                            chat.selectedClaw?.clawId ?? '选择设备',
                                            style: const TextStyle(
                                              fontSize: 12,
                                              color:
                                                  _OhsdChatTheme.textSecondary,
                                            ),
                                            overflow: TextOverflow.ellipsis,
                                          ),
                                        ),
                                        Opacity(
                                          opacity: 0.55,
                                          child: Image.asset(
                                            'assets/down.png',
                                            width: 13,
                                            height: 13,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ),
                        ),
                        const SizedBox(width: 6),
                        PopupMenuButton<int>(
                          padding: EdgeInsets.zero,
                          offset: const Offset(0, 34),
                          color: _OhsdChatTheme.bgSurface,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(12),
                          ),
                          onSelected: (i) =>
                              chat.setSession(_sessionOptions[i].value),
                          itemBuilder: (ctx) => [
                            for (var i = 0; i < _sessionOptions.length; i++)
                              PopupMenuItem(
                                value: i,
                                child: Text(_sessionOptions[i].label),
                              ),
                          ],
                          child: _ToolChip(
                            minWidth: 72,
                            maxWidth: 120,
                            child: Row(
                              children: [
                                Expanded(
                                  child: Text(
                                    _sessionOptions[sessionIndex].label,
                                    style: const TextStyle(
                                      fontSize: 12,
                                      color: _OhsdChatTheme.textSecondary,
                                    ),
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                                Opacity(
                                  opacity: 0.55,
                                  child: Image.asset(
                                    'assets/down.png',
                                    width: 13,
                                    height: 13,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        _ToolIconButton(
                          tooltip: '刷新',
                          onTap: () async {
                            final ok = await chat.fetchClawStatus();
                            final c = chat.selectedClaw;
                            if (c != null && chat.currentSession == 'main') {
                              await chat.loadHistory(c.clawId);
                            }
                            if (!context.mounted) return;
                            final msg = ok
                                ? '刷新完成：在线设备 ${chat.clawList.length} 台'
                                : '刷新失败：${chat.lastDeviceRefreshError ?? '请检查网络/后端'}';
                            ScaffoldMessenger.of(
                              context,
                            ).showSnackBar(SnackBar(content: Text(msg)));
                          },
                          child: Opacity(
                            opacity: 0.65,
                            child: Image.asset(
                              'assets/refresh.png',
                              width: 18,
                              height: 18,
                            ),
                          ),
                        ),
                        const SizedBox(width: 6),
                        _ToolIconButton(
                          tooltip: '新对话（清空历史）',
                          onTap: sendLoading
                              ? null
                              : () async {
                                  await chat.startNewConversation();
                                  if (mounted) _inputCtl.clear();
                                },
                          child: Icon(
                            Icons.add_comment_outlined,
                            size: 21,
                            color: _OhsdChatTheme.toolIcon,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),

                Expanded(
                  child: Container(
                    color: _OhsdChatTheme.pageBg,
                    child: Consumer<ChatController>(
                      builder: (context, chat, _) {
                        if (chat.messages.isEmpty) {
                          return _buildWelcome(chat);
                        }
                        return ListView.builder(
                          controller: _scrollCtl,
                          padding: const EdgeInsets.fromLTRB(10, 12, 10, 4),
                          itemCount: chat.messages.length,
                          itemBuilder: (context, idx) {
                            final msg = chat.messages[idx];
                            return KeyedSubtree(
                              key: ValueKey('${msg.role}:${msg.messageId}'),
                              child: _MessageBubble(msg: msg),
                            );
                          },
                        );
                      },
                    ),
                  ),
                ),

                SafeArea(
                  top: false,
                  child: Container(
                    color: _OhsdChatTheme.bgSurface,
                    padding: const EdgeInsets.fromLTRB(10, 6, 10, 10),
                    child: IgnorePointer(
                      ignoring: composerLocked,
                      child: Opacity(
                        opacity: composerLocked ? 0.6 : 1.0,
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            if (chat.attachments.isNotEmpty)
                          Container(
                            width: double.infinity,
                            margin: const EdgeInsets.only(bottom: 6),
                            padding: const EdgeInsets.all(6),
                            decoration: BoxDecoration(
                              color: const Color(0x05000000),
                              borderRadius: BorderRadius.circular(8),
                              border: const Border(
                                bottom: BorderSide(
                                  color: _OhsdChatTheme.borderHairline,
                                ),
                              ),
                            ),
                            child: Wrap(
                              spacing: 6,
                              runSpacing: 6,
                              alignment: WrapAlignment.center,
                              children: [
                                for (final att in chat.attachments)
                                  _ComposerAttachmentTile(
                                    att: att,
                                    imageProvider: _attachmentImageProvider(
                                      att,
                                    ),
                                    onRemove: () =>
                                        chat.removeAttachment(att.uid),
                                    onOpen: () {
                                      if (att.isImage) {
                                        _showImagePreview(context, att);
                                        return;
                                      }
                                      final auth = context.read<AuthController>();
                                      unawaited(
                                        _showDocumentActionsSheet(
                                          context,
                                          att,
                                          auth.token,
                                        ),
                                      );
                                    },
                                  ),
                              ],
                            ),
                          ),
                            AnimatedContainer(
                          duration: const Duration(milliseconds: 200),
                          padding: const EdgeInsets.fromLTRB(9, 8, 9, 7),
                          decoration: BoxDecoration(
                            color: _OhsdChatTheme.bgSurface,
                            borderRadius: BorderRadius.circular(14),
                            border: Border.all(
                              color: _inputFocused
                                  ? _OhsdChatTheme.primary
                                  : _OhsdChatTheme.borderHairline,
                            ),
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              if (_voiceMode)
                                Material(
                                  color: _OhsdChatTheme.bgPage,
                                  borderRadius: BorderRadius.circular(9),
                                  child: GestureDetector(
                                    onLongPressStart: (_) {
                                      unawaited(_onVoiceLongPressStart());
                                    },
                                    onLongPressEnd: (_) {
                                      unawaited(_onVoiceLongPressEnd());
                                    },
                                    onLongPressCancel: () {
                                      unawaited(_onVoiceLongPressCancel());
                                    },
                                    child: Container(
                                      alignment: Alignment.center,
                                      height: 36,
                                      decoration: BoxDecoration(
                                        borderRadius: BorderRadius.circular(9),
                                        border: Border.all(
                                          color: _OhsdChatTheme.borderLight,
                                        ),
                                      ),
                                      child: Text(
                                        _voiceRecording
                                            ? '松开结束并识别'
                                            : '长按说话，松手识别',
                                        style: const TextStyle(
                                          fontSize: 12,
                                          color: _OhsdChatTheme.textSecondary,
                                        ),
                                      ),
                                    ),
                                  ),
                                )
                              else
                                TextField(
                                  enabled: !composerLocked,
                                  controller: _inputCtl,
                                  focusNode: _inputFocus,
                                  maxLength: 500,
                                  maxLines: 4,
                                  minLines: 1,
                                  keyboardType: TextInputType.multiline,
                                  textInputAction: TextInputAction.send,
                                  onSubmitted: composerLocked
                                      ? null
                                      : (_) => _sendMessage(),
                                  style: const TextStyle(
                                    fontSize: 12,
                                    color: Color(0xD9000000),
                                    height: 1.4,
                                  ),
                                  decoration: const InputDecoration(
                                    isDense: true,
                                    hintText: '请输入消息内容...',
                                    hintStyle: TextStyle(
                                      fontSize: 12,
                                      color: _OhsdChatTheme.textPlaceholder,
                                      height: 1.4,
                                    ),
                                    border: InputBorder.none,
                                    contentPadding: EdgeInsets.symmetric(
                                      vertical: 9,
                                    ),
                                    counterText: '',
                                  ),
                                ),
                              const SizedBox(height: 6),
                              Row(
                                crossAxisAlignment: CrossAxisAlignment.center,
                                children: [
                                  _ChatInputSideButton(
                                    icon: Icons.attach_file,
                                    label: '附件',
                                    onTap: composerLocked ? null : _pickFiles,
                                  ),
                                  const SizedBox(width: 6),
                                  _ChatInputSideButton(
                                    icon: Icons.mic_none_outlined,
                                    label: '语音',
                                    active: _voiceUiEnabled,
                                    onTap: composerLocked
                                        ? null
                                        : () => setState(() {
                                              _voiceUiEnabled = !_voiceUiEnabled;
                                              _voiceMode = _voiceUiEnabled;
                                            }),
                                  ),
                                  const Spacer(),
                                  Material(
                                    color: Colors.transparent,
                                    child: InkWell(
                                      onTap: sendLoading
                                          ? null
                                          : () async {
                                              await _sendMessage();
                                            },
                                      customBorder: const CircleBorder(),
                                      child: Ink(
                                        width: 26,
                                        height: 26,
                                        decoration: BoxDecoration(
                                          color: sendActive || sendLoading
                                              ? _OhsdChatTheme.primary
                                              : _OhsdChatTheme.primarySoft,
                                          borderRadius: BorderRadius.circular(
                                            9,
                                          ),
                                        ),
                                        child: Center(
                                          child: sendLoading
                                              ? const SizedBox(
                                                  width: 14,
                                                  height: 14,
                                                  child:
                                                      CircularProgressIndicator(
                                                        strokeWidth: 2,
                                                        color: Colors.white,
                                                      ),
                                                )
                                              : ColorFiltered(
                                                  colorFilter:
                                                      const ColorFilter.mode(
                                                        Colors.white,
                                                        BlendMode.srcIn,
                                                      ),
                                                  child: Image.asset(
                                                    'assets/arrow-top.png',
                                                    width: 17,
                                                    height: 17,
                                                  ),
                                                ),
                                        ),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _buildNavigationDrawer(
    BuildContext context,
    AuthController auth,
    ChatController chat,
  ) {
    return Drawer(
      backgroundColor: _OhsdChatTheme.bgSurface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.only(
          topRight: Radius.circular(12),
          bottomRight: Radius.circular(12),
        ),
      ),
      child: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 4, 12),
              child: Row(
                children: [
                  Container(
                    width: 40,
                    height: 40,
                    decoration: BoxDecoration(
                      color: _OhsdChatTheme.bgIos,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    alignment: Alignment.center,
                    child: Image.network(
                      _OhsdChatTheme.logoNetworkUrl,
                      width: 22,
                      height: 22,
                      fit: BoxFit.contain,
                      errorBuilder: (context, error, stackTrace) => Padding(
                        padding: const EdgeInsets.all(6),
                        child: SvgPicture.asset(
                          'assets/logo.svg',
                          fit: BoxFit.contain,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  const Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '会宝',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w700,
                            color: _OhsdChatTheme.textPrimary,
                          ),
                        ),
                        SizedBox(height: 2),
                        Text(
                          '导航',
                          style: TextStyle(
                            fontSize: 12,
                            color: _OhsdChatTheme.textSubtle,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    icon: const Icon(
                      Icons.close,
                      size: 22,
                      color: _OhsdChatTheme.textSecondary,
                    ),
                    onPressed: () => Navigator.of(context).pop(),
                  ),
                ],
              ),
            ),
            const Divider(
              height: 1,
              thickness: 1,
              color: _OhsdChatTheme.borderHairline,
            ),
            Expanded(
              child: ListView(
                padding: const EdgeInsets.symmetric(
                  horizontal: 8,
                  vertical: 10,
                ),
                children: [
                  /*
                  _drawerNavRow(
                    icon: Icons.account_balance_wallet_outlined,
                    title: '账号余额',
                    onTap: () {
                      Navigator.of(context).pop();
                      Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const _AccountBalancePage(),
                        ),
                      );
                    },
                  ),
                  */
                  _drawerNavRow(
                    icon: Icons.person_outline_rounded,
                    title: '个人中心',
                    onTap: () {
                      Navigator.of(context).pop();
                      Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) =>
                              _ProfileCenterPage(auth: auth, chat: chat),
                        ),
                      );
                    },
                  ),
                  _drawerNavRow(
                    icon: Icons.description_outlined,
                    title: '服务协议',
                    onTap: () {
                      Navigator.of(context).pop();
                      Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const _ServiceAgreementPage(),
                        ),
                      );
                    },
                  ),
                  _drawerNavRow(
                    icon: Icons.menu_book_outlined,
                    title: 'openHSD 教程',
                    onTap: () {
                      Navigator.of(context).pop();
                      Navigator.of(context).pushNamed('/tutorial');
                    },
                  ),
                  _drawerNavRow(
                    icon: Icons.settings_outlined,
                    title: '设置',
                    onTap: () {
                      Navigator.of(context).pop();
                      Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => const _SettingsPage(),
                        ),
                      );
                    },
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _drawerNavRow({
    required IconData icon,
    required String title,
    required VoidCallback onTap,
  }) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(10),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(10),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 10),
            child: Row(
              children: [
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: _OhsdChatTheme.fill05,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  alignment: Alignment.center,
                  child: Icon(icon, size: 22, color: _OhsdChatTheme.primary),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 15,
                      fontWeight: FontWeight.w500,
                      color: _OhsdChatTheme.textPrimary,
                    ),
                  ),
                ),
                const Icon(
                  Icons.chevron_right,
                  size: 22,
                  color: _OhsdChatTheme.textPlaceholder,
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildWelcome(ChatController chat) {
    Future<void> onPromptSend(String payload) async {
      final t = payload.length > 500 ? payload.substring(0, 500) : payload;
      _inputCtl.text = t;
      chat.setComposerInput(t);
      await _sendMessage();
    }

    const prompts = <({String display, String send})>[
      (display: '项目是做什么的？', send: '这个项目是做什么的？'),
      (display: '怎么用这个聊天？', send: '如何使用这个对话界面？'),
      (display: '有哪些技术特点？', send: '有哪些技术特点？'),
    ];

    return ListView(
      padding: const EdgeInsets.fromLTRB(14, 16, 14, 12),
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            SizedBox(
              width: 40,
              height: 40,
              child: ClipRRect(
                borderRadius: BorderRadius.circular(10),
                child: Image.network(
                  _OhsdChatTheme.welcomeIconNetworkUrl,
                  fit: BoxFit.contain,
                  errorBuilder: (context, error, stackTrace) =>
                      SvgPicture.asset('assets/logo.svg', fit: BoxFit.contain),
                ),
              ),
            ),
            const SizedBox(width: 10),
            const Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '你好，我是 AI 助手',
                    style: TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                      color: _OhsdChatTheme.textPrimary,
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    '网关直连对话，与 Web 端同一套风格，移动端已为你简化布局。',
                    style: TextStyle(
                      fontSize: 12,
                      color: _OhsdChatTheme.textTertiary,
                      height: 1.5,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 14),
        DecoratedBox(
          decoration: BoxDecoration(
            gradient: _OhsdChatTheme.welcomeCardGrad,
            borderRadius: BorderRadius.circular(8),
          ),
          child: Padding(
            padding: const EdgeInsets.fromLTRB(13, 12, 13, 10),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Text(
                  '试试这样问',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    color: _OhsdChatTheme.textSecondary,
                  ),
                ),
                const SizedBox(height: 4),
                for (var i = 0; i < prompts.length; i++) ...[
                  Material(
                    color: Colors.transparent,
                    child: InkWell(
                      onTap: () => onPromptSend(prompts[i].send),
                      borderRadius: BorderRadius.circular(4),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 6),
                        child: Text(
                          prompts[i].display,
                          style: const TextStyle(
                            fontSize: 13,
                            color: _OhsdChatTheme.textSecondary,
                            height: 1.55,
                          ),
                        ),
                      ),
                    ),
                  ),
                  if (i < prompts.length - 1)
                    const Divider(
                      height: 1,
                      thickness: 1,
                      color: _OhsdChatTheme.borderHairline,
                    ),
                ],
              ],
            ),
          ),
        ),
      ],
    );
  }

  void _showUserPanel(AuthController auth, ChatController chat) {
    var tokenCopied = false;
    final topPad = MediaQuery.paddingOf(context).top;

    showDialog<void>(
      context: context,
      barrierColor: _OhsdChatTheme.overlayLight,
      builder: (dialogContext) {
        return ListenableBuilder(
          listenable: Listenable.merge([auth, chat]),
          builder: (context, _) {
            final userInitial = (auth.user?.username.isNotEmpty ?? false)
                ? auth.user!.username[0].toUpperCase()
                : 'U';
            final h = MediaQuery.sizeOf(context).height;

            return Dialog(
              alignment: Alignment.topCenter,
              backgroundColor: Colors.transparent,
              elevation: 0,
              insetPadding: EdgeInsets.fromLTRB(12, topPad + 48, 12, 24),
              child: StatefulBuilder(
                builder: (context, setModal) {
                  return Material(
                    color: _OhsdChatTheme.bgSurface,
                    elevation: 6,
                    shadowColor: const Color(0x1F000000),
                    borderRadius: BorderRadius.circular(10),
                    clipBehavior: Clip.antiAlias,
                    child: ConstrainedBox(
                      constraints: BoxConstraints(maxHeight: h * 0.72),
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.stretch,
                          children: [
                            // 头部：红渐变头像 + 用户名（chat.vue .panel-user-head）
                            Padding(
                              padding: const EdgeInsets.fromLTRB(
                                12,
                                14,
                                12,
                                12,
                              ),
                              child: Row(
                                children: [
                                  Container(
                                    width: 28,
                                    height: 28,
                                    decoration: const BoxDecoration(
                                      shape: BoxShape.circle,
                                      gradient: LinearGradient(
                                        begin: Alignment.topLeft,
                                        end: Alignment.bottomRight,
                                        colors: [
                                          _OhsdChatTheme.error,
                                          _OhsdChatTheme.dangerDeep,
                                        ],
                                      ),
                                    ),
                                    alignment: Alignment.center,
                                    child: Text(
                                      userInitial,
                                      style: const TextStyle(
                                        fontSize: 12,
                                        fontWeight: FontWeight.w700,
                                        color: Colors.white,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  Expanded(
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.start,
                                      children: [
                                        Text(
                                          auth.user?.username ?? '',
                                          style: const TextStyle(
                                            fontSize: 13,
                                            fontWeight: FontWeight.w600,
                                            color: _OhsdChatTheme.headingStrong,
                                          ),
                                        ),
                                        const SizedBox(height: 3),
                                        Text(
                                          'ID: ${auth.user?.userId ?? ''}',
                                          style: const TextStyle(
                                            fontSize: 10,
                                            color: _OhsdChatTheme.captionHex,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  IconButton(
                                    visualDensity: VisualDensity.compact,
                                    icon: const Icon(
                                      Icons.close,
                                      size: 20,
                                      color: _OhsdChatTheme.textSecondary,
                                    ),
                                    onPressed: () =>
                                        Navigator.of(dialogContext).pop(),
                                  ),
                                ],
                              ),
                            ),
                            const Divider(
                              height: 1,
                              thickness: 1,
                              color: _OhsdChatTheme.panelDivider,
                            ),
                            // TOKEN
                            Padding(
                              padding: const EdgeInsets.fromLTRB(12, 13, 12, 0),
                              child: Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text(
                                    'TOKEN',
                                    style: TextStyle(
                                      fontSize: 9,
                                      fontWeight: FontWeight.w600,
                                      color: _OhsdChatTheme.captionHex,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 8,
                                    ),
                                    decoration: BoxDecoration(
                                      color: _OhsdChatTheme.bgMuted,
                                      borderRadius: BorderRadius.circular(6),
                                    ),
                                    constraints: const BoxConstraints(
                                      maxHeight: 50,
                                    ),
                                    child: SingleChildScrollView(
                                      child: SelectableText(
                                        auth.token,
                                        style: const TextStyle(
                                          fontSize: 9,
                                          fontFamily: 'monospace',
                                          height: 1.5,
                                          color: _OhsdChatTheme.tokenBodyHex,
                                        ),
                                      ),
                                    ),
                                  ),
                                  const SizedBox(height: 8),
                                  Material(
                                    color: _OhsdChatTheme.fill05,
                                    borderRadius: BorderRadius.circular(5),
                                    child: InkWell(
                                      onTap: () {
                                        ClipboardStatus.setClipboard(
                                          auth.token,
                                        );
                                        setModal(() => tokenCopied = true);
                                        Future<void>.delayed(
                                          const Duration(milliseconds: 1500),
                                          () {
                                            if (!context.mounted) return;
                                            setModal(() => tokenCopied = false);
                                          },
                                        );
                                      },
                                      borderRadius: BorderRadius.circular(5),
                                      child: Container(
                                        width: double.infinity,
                                        padding: const EdgeInsets.symmetric(
                                          vertical: 10,
                                        ),
                                        alignment: Alignment.center,
                                        child: Text(
                                          tokenCopied ? '已复制!' : '复制',
                                          style: const TextStyle(
                                            fontSize: 11,
                                            color: _OhsdChatTheme.textSecondary,
                                          ),
                                        ),
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Padding(
                              padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
                              child: Container(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 8,
                                  vertical: 6,
                                ),
                                decoration: BoxDecoration(
                                  color: _OhsdChatTheme.surfaceTintBlue,
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: const Text(
                                  '将此 Token 填入插件 cj.config.json → cloud.token',
                                  style: TextStyle(
                                    fontSize: 9,
                                    color: _OhsdChatTheme.primary,
                                    height: 1.5,
                                  ),
                                ),
                              ),
                            ),
                            // 在线设备
                            Padding(
                              padding: const EdgeInsets.fromLTRB(12, 13, 12, 0),
                              child: Row(
                                children: [
                                  const Text(
                                    '在线设备',
                                    style: TextStyle(
                                      fontSize: 9,
                                      fontWeight: FontWeight.w600,
                                      color: _OhsdChatTheme.captionHex,
                                      letterSpacing: 0.5,
                                    ),
                                  ),
                                  const Spacer(),
                                  TextButton(
                                    style: TextButton.styleFrom(
                                      padding: const EdgeInsets.symmetric(
                                        horizontal: 4,
                                      ),
                                      minimumSize: Size.zero,
                                      tapTargetSize:
                                          MaterialTapTargetSize.shrinkWrap,
                                    ),
                                    onPressed: chat.loadingDevices
                                        ? null
                                        : () async {
                                            final ok = await chat
                                                .fetchClawStatus();
                                            if (!context.mounted) return;
                                            final msg = ok
                                                ? '刷新完成：在线设备 ${chat.clawList.length} 台'
                                                : '刷新失败：${chat.lastDeviceRefreshError ?? '请检查网络/后端'}';
                                            ScaffoldMessenger.of(
                                              context,
                                            ).showSnackBar(
                                              SnackBar(content: Text(msg)),
                                            );
                                          },
                                    child: Text(
                                      chat.loadingDevices ? '刷新中' : '刷新',
                                      style: const TextStyle(
                                        fontSize: 10,
                                        color: _OhsdChatTheme.primary,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            Padding(
                              padding: const EdgeInsets.fromLTRB(12, 0, 12, 8),
                              child: chat.clawList.isEmpty
                                  ? Padding(
                                      padding: const EdgeInsets.symmetric(
                                        vertical: 8,
                                      ),
                                      child: Text(
                                        '暂无在线设备',
                                        textAlign: TextAlign.center,
                                        style: TextStyle(
                                          fontSize: 11,
                                          color: _OhsdChatTheme.textTertiary,
                                        ),
                                      ),
                                    )
                                  : Column(
                                      children: [
                                        for (final d in chat.clawList)
                                          Padding(
                                            padding: const EdgeInsets.only(
                                              bottom: 5,
                                            ),
                                            child: Container(
                                              width: double.infinity,
                                              padding:
                                                  const EdgeInsets.fromLTRB(
                                                    9,
                                                    7,
                                                    9,
                                                    7,
                                                  ),
                                              decoration: BoxDecoration(
                                                color:
                                                    _OhsdChatTheme.clawCardBg,
                                                borderRadius:
                                                    BorderRadius.circular(6),
                                                border: Border.all(
                                                  color: _OhsdChatTheme
                                                      .clawCardBorder,
                                                ),
                                              ),
                                              child: Column(
                                                crossAxisAlignment:
                                                    CrossAxisAlignment.start,
                                                children: [
                                                  Row(
                                                    children: [
                                                      Container(
                                                        width: 5,
                                                        height: 5,
                                                        decoration:
                                                            const BoxDecoration(
                                                              color:
                                                                  _OhsdChatTheme
                                                                      .success,
                                                              shape: BoxShape
                                                                  .circle,
                                                            ),
                                                      ),
                                                      const SizedBox(width: 5),
                                                      Expanded(
                                                        child: Text(
                                                          d.clawId,
                                                          style: const TextStyle(
                                                            fontSize: 11,
                                                            fontWeight:
                                                                FontWeight.w600,
                                                            color: _OhsdChatTheme
                                                                .headingStrong,
                                                          ),
                                                        ),
                                                      ),
                                                    ],
                                                  ),
                                                  const SizedBox(height: 2),
                                                  Padding(
                                                    padding:
                                                        const EdgeInsets.only(
                                                          left: 10,
                                                        ),
                                                    child: Text(
                                                      _formatHeartbeat(
                                                        d.lastHeartbeat,
                                                      ),
                                                      style: const TextStyle(
                                                        fontSize: 9,
                                                        color: Color(
                                                          0xFFAAAAAA,
                                                        ),
                                                      ),
                                                    ),
                                                  ),
                                                ],
                                              ),
                                            ),
                                          ),
                                      ],
                                    ),
                            ),
                            Padding(
                              padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
                              child: Material(
                                color: _OhsdChatTheme.dangerFaintBg,
                                borderRadius: BorderRadius.circular(6),
                                child: InkWell(
                                  onTap: () async {
                                    Navigator.of(dialogContext).pop();
                                    chat.ws.disconnect();
                                    await auth.logout();
                                    if (!mounted) return;
                                    Navigator.of(
                                      this.context,
                                    ).pushReplacementNamed('/login');
                                  },
                                  borderRadius: BorderRadius.circular(6),
                                  child: Container(
                                    width: double.infinity,
                                    padding: const EdgeInsets.symmetric(
                                      vertical: 11,
                                    ),
                                    alignment: Alignment.center,
                                    child: const Text(
                                      '退出登录',
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: _OhsdChatTheme.error,
                                        fontWeight: FontWeight.w500,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
            );
          },
        );
      },
    );
  }

  String _formatHeartbeat(int ts) {
    if (ts <= 0) return '未知';
    final diff = DateTime.now().millisecondsSinceEpoch - ts;
    if (diff < 60000) return '${diff ~/ 1000}s前';
    return '${diff ~/ 60000}min前';
  }
}

class _ToolChip extends StatelessWidget {
  final double minWidth;
  final double maxWidth;
  final Widget child;
  final BorderStyle borderStyle;
  final Color? background;

  const _ToolChip({
    required this.minWidth,
    required this.maxWidth,
    required this.child,
    this.borderStyle = BorderStyle.solid,
    this.background,
  });

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: BoxConstraints(
        minWidth: minWidth,
        maxWidth: maxWidth,
        minHeight: 32,
        maxHeight: 32,
      ),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 10),
        decoration: BoxDecoration(
          color: background ?? _OhsdChatTheme.bgSurface,
          borderRadius: BorderRadius.circular(6),
          border: Border.all(
            color: _OhsdChatTheme.borderLight,
            style: borderStyle,
          ),
        ),
        alignment: Alignment.centerLeft,
        child: child,
      ),
    );
  }
}

class _ToolIconButton extends StatelessWidget {
  final String tooltip;
  final VoidCallback? onTap;
  final Widget child;

  const _ToolIconButton({
    required this.tooltip,
    required this.onTap,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    final disabled = onTap == null;
    return Tooltip(
      message: tooltip,
      child: Material(
        color: _OhsdChatTheme.bgSurface,
        borderRadius: BorderRadius.circular(6),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(6),
          child: Container(
            width: 32,
            height: 32,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(6),
              border: Border.all(color: _OhsdChatTheme.borderLight),
            ),
            child: Opacity(opacity: disabled ? 0.4 : 1, child: child),
          ),
        ),
      ),
    );
  }
}

class _ChatInputSideButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback? onTap;
  final bool active;

  const _ChatInputSideButton({
    required this.icon,
    required this.label,
    required this.onTap,
    this.active = false,
  });

  @override
  Widget build(BuildContext context) {
    final disabled = onTap == null;
    final fg = active ? Colors.white : _OhsdChatTheme.textSecondary;
    final bg = active ? _OhsdChatTheme.primary : _OhsdChatTheme.bgPage;
    final bd = active ? _OhsdChatTheme.primary : _OhsdChatTheme.borderLight;
    return Material(
      color: bg,
      borderRadius: BorderRadius.circular(9),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(9),
        child: Container(
          height: 26,
          padding: const EdgeInsets.symmetric(horizontal: 8),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(9),
            border: Border.all(color: bd),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Opacity(
                opacity: disabled ? 0.45 : 1,
                child: Icon(icon, size: 18, color: fg),
              ),
              const SizedBox(width: 5),
              Opacity(
                opacity: disabled ? 0.45 : 1,
                child: Text(
                  label,
                  style: TextStyle(fontSize: 11, color: fg, height: 1),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ComposerAttachmentTile extends StatelessWidget {
  final ChatAttachment att;
  final ImageProvider imageProvider;
  final VoidCallback onRemove;
  final VoidCallback onOpen;

  const _ComposerAttachmentTile({
    required this.att,
    required this.imageProvider,
    required this.onRemove,
    required this.onOpen,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      clipBehavior: Clip.none,
      children: [
        if (att.isImage)
          GestureDetector(
            onTap: onOpen,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: Image(
                image: imageProvider,
                width: 48,
                height: 48,
                fit: BoxFit.cover,
              ),
            ),
          )
        else
          GestureDetector(
            onTap: onOpen,
            child: Container(
              width: 60,
              padding: const EdgeInsets.symmetric(vertical: 6, horizontal: 4),
              decoration: BoxDecoration(
                color: const Color(0x0A000000),
                borderRadius: BorderRadius.circular(6),
                border: Border.all(color: const Color(0x14000000)),
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Image.asset('assets/document.png', width: 22, height: 22),
                  Text(
                    att.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(
                      fontSize: 9,
                      color: _OhsdChatTheme.textSecondary,
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ),
        Positioned(
          right: -4,
          top: -4,
          child: GestureDetector(
            onTap: onRemove,
            child: Container(
              width: 18,
              height: 18,
              decoration: const BoxDecoration(
                color: Color(0x8C000000),
                shape: BoxShape.circle,
              ),
              alignment: Alignment.center,
              child: const Text(
                '×',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 12,
                  fontWeight: FontWeight.bold,
                  height: 1,
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}

class _AssistantAvatar extends StatelessWidget {
  const _AssistantAvatar();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 28,
      height: 28,
      margin: const EdgeInsets.only(top: 2),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: const Color(0xFFF0F0F5),
        border: Border.all(color: _OhsdChatTheme.borderHairline),
      ),
      alignment: Alignment.center,
      child: Image.network(
        _OhsdChatTheme.logoNetworkUrl,
        width: 14,
        height: 14,
        fit: BoxFit.contain,
        errorBuilder: (context, error, stackTrace) => SvgPicture.asset(
          'assets/logo.svg',
          width: 14,
          height: 14,
          fit: BoxFit.contain,
        ),
      ),
    );
  }
}

class _UserAvatar extends StatelessWidget {
  const _UserAvatar();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 28,
      height: 28,
      margin: const EdgeInsets.only(top: 2),
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: const Color(0xFFEAF1FF),
        border: Border.all(color: _OhsdChatTheme.borderHairline),
      ),
      alignment: Alignment.center,
      child: const Icon(Icons.person, size: 14, color: Color(0xFF5D6B86)),
    );
  }
}

class _MessageBubble extends StatelessWidget {
  final ChatMessage msg;
  const _MessageBubble({required this.msg});

  static const _bubbleRadius = 10.0;

  @override
  Widget build(BuildContext context) {
    final isUser = msg.role == 'user';
    final isAborting = context.select<ChatController, bool>(
      (c) => c.abortingMessageIds.contains(msg.messageId),
    );
    final imageAttachments = msg.attachments
        .where((a) => a.isImage)
        .take(6)
        .toList();
    final fileAttachments = msg.attachments.where((a) => !a.isImage).toList();

    final bubbleDecoration = BoxDecoration(
      color: _OhsdChatTheme.bgIos,
      borderRadius: const BorderRadius.only(
        topLeft: Radius.zero,
        topRight: Radius.zero,
        bottomLeft: Radius.circular(_bubbleRadius),
        bottomRight: Radius.circular(_bubbleRadius),
      ),
    );

    Widget bubbleChild;
    if (msg.role == 'assistant' && msg.loading && msg.content.isEmpty) {
      bubbleChild = const Padding(
        padding: EdgeInsets.symmetric(vertical: 4),
        child: _TypingDots(),
      );
    } else if (isUser) {
      bubbleChild = SelectableText(
        msg.content,
        style: const TextStyle(
          fontSize: 13.5,
          color: Color(0xFF0A0A0A),
          height: 1.6,
        ),
      );
    } else {
      String resolveMarkdownImageSrc(Uri uri) {
        final raw = uri.toString();
        if (raw.startsWith('data:')) return raw;
        if (uri.hasScheme) return raw;
        final base = AppConfig.httpBaseUrl.trim().replaceAll(RegExp(r'/+$'), '');
        if (raw.startsWith('/')) return '$base$raw';
        return '$base/$raw';
      }

      ChatAttachment buildMarkdownImageAttachment(Uri uri, String? alt) {
        final raw = uri.toString();
        final fallbackName = () {
          final seg =
              uri.pathSegments.isNotEmpty ? uri.pathSegments.last : 'image';
          final safe = _sanitizeFileName(seg.isEmpty ? 'image' : seg);
          // 给无后缀的资源一个默认后缀，避免保存时没有扩展名
          if (!safe.contains('.')) return '$safe.png';
          return safe;
        }();
        return ChatAttachment(
          uid: 'md_${raw.hashCode}',
          name: _sanitizeFileName((alt ?? '').trim().isEmpty ? fallbackName : alt!.trim()),
          isImage: true,
          url: raw.startsWith('data:') ? null : resolveMarkdownImageSrc(uri),
          dataUri: raw.startsWith('data:') ? raw : null,
        );
      }

      bubbleChild = MarkdownBody(
        data: msg.content,
        selectable: true,
        imageBuilder: (uri, title, alt) {
          final att = buildMarkdownImageAttachment(uri, alt);
          final src = att.dataUri ?? att.url ?? '';
          final child = src.startsWith('data:')
              ? Image.memory(
                  base64Decode(src.split(',').last),
                  fit: BoxFit.cover,
                )
              : Image.network(
                  src,
                  fit: BoxFit.cover,
                  errorBuilder: (_, __, ___) => const Center(
                    child: Text('图片加载失败'),
                  ),
                );
          return Padding(
            padding: const EdgeInsets.symmetric(vertical: 6),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Material(
                color: const Color(0xFFF4F4F4),
                child: InkWell(
                  onTap: () => _showImagePreview(context, att),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(
                      maxWidth: 260,
                      maxHeight: 260,
                      minWidth: 90,
                      minHeight: 70,
                    ),
                    child: child,
                  ),
                ),
              ),
            ),
          );
        },
        styleSheet: MarkdownStyleSheet(
          p: const TextStyle(
            height: 1.65,
            fontSize: 13.5,
            color: _OhsdChatTheme.textPrimary,
          ),
          code: const TextStyle(fontFamily: 'monospace'),
        ),
      );
    }

    final bubbleBody = Container(
      width: isUser ? null : double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
      constraints: BoxConstraints(
        maxWidth: MediaQuery.sizeOf(context).width * 0.75,
      ),
      decoration: bubbleDecoration,
      child: bubbleChild,
    );

    final bubbleColumn = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: isUser
          ? CrossAxisAlignment.end
          : CrossAxisAlignment.start,
      children: [
        if (isUser && msg.attachments.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: _MessageAttachments(
              isUser: isUser,
              imageAttachments: imageAttachments,
              fileAttachments: fileAttachments,
            ),
          ),
        if (!isUser && msg.attachments.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: _MessageAttachments(
              isUser: isUser,
              imageAttachments: imageAttachments,
              fileAttachments: fileAttachments,
            ),
          ),
        isUser
            ? bubbleBody
            : GestureDetector(
                onLongPress: () => _copyMessageContent(context, msg.content),
                child: bubbleBody,
              ),
        if (!isUser && msg.loading)
          Padding(
            padding: const EdgeInsets.only(top: 6),
            child: OutlinedButton.icon(
              onPressed: isAborting
                  ? null
                  : () async {
                      final res = await context
                          .read<ChatController>()
                          .abortAssistantMessage(msg.messageId);
                      if (!context.mounted) return;
                      final tip =
                          (res['message'] ?? (res['success'] == true ? '已发送停止指令' : '停止失败'))
                              .toString();
                      ScaffoldMessenger.of(
                        context,
                      ).showSnackBar(SnackBar(content: Text(tip)));
                    },
              icon: isAborting
                  ? const SizedBox(
                      width: 12,
                      height: 12,
                      child: CircularProgressIndicator(strokeWidth: 1.8),
                    )
                  : const Icon(Icons.stop_circle_outlined, size: 16),
              label: Text(isAborting ? '停止中...' : '停止'),
              style: OutlinedButton.styleFrom(
                foregroundColor: _OhsdChatTheme.error,
                side: const BorderSide(color: _OhsdChatTheme.error),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                minimumSize: const Size(0, 28),
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                visualDensity: VisualDensity.compact,
              ),
            ),
          ),
      ],
    );

    if (isUser) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 14),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            Flexible(child: bubbleColumn),
            const SizedBox(width: 7),
            const _UserAvatar(),
          ],
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.only(bottom: 14),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const _AssistantAvatar(),
          const SizedBox(width: 7),
          Expanded(child: bubbleColumn),
        ],
      ),
    );
  }
}

class _MessageAttachments extends StatelessWidget {
  final bool isUser;
  final List<ChatAttachment> imageAttachments;
  final List<ChatAttachment> fileAttachments;

  const _MessageAttachments({
    required this.isUser,
    required this.imageAttachments,
    required this.fileAttachments,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: isUser
          ? CrossAxisAlignment.end
          : CrossAxisAlignment.start,
      children: [
        if (imageAttachments.isNotEmpty)
          Wrap(
            spacing: 3,
            runSpacing: 3,
            alignment: isUser ? WrapAlignment.end : WrapAlignment.start,
            children: imageAttachments
                .map(
                  (att) => ClipRRect(
                    borderRadius: BorderRadius.circular(6),
                    child: _MessageImageThumb(
                      att: att,
                      onTap: () => _showImagePreview(context, att),
                    ),
                  ),
                )
                .toList(),
          ),
        if (fileAttachments.isNotEmpty)
          Padding(
            padding: EdgeInsets.only(top: imageAttachments.isNotEmpty ? 5 : 0),
            child: Wrap(
              spacing: 4,
              runSpacing: 4,
              alignment: isUser ? WrapAlignment.end : WrapAlignment.start,
              children: fileAttachments
                  .map(
                    (att) => _MessageFileTile(
                      att: att,
                      onOpen: () async {
                        final auth = context.read<AuthController>();
                        await _showDocumentActionsSheet(
                          context,
                          att,
                          auth.token,
                        );
                      },
                    ),
                  )
                  .toList(),
            ),
          ),
      ],
    );
  }
}

class _MessageImageThumb extends StatelessWidget {
  final ChatAttachment att;
  final VoidCallback onTap;

  const _MessageImageThumb({required this.att, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final src = att.dataUri ?? att.url ?? '';
    Widget img;
    if (src.startsWith('http://') || src.startsWith('https://')) {
      img = Image.network(src, width: 60, height: 60, fit: BoxFit.cover);
    } else if (src.startsWith('data:')) {
      try {
        img = Image.memory(
          base64Decode(src.split(',').last),
          width: 60,
          height: 60,
          fit: BoxFit.cover,
        );
      } catch (_) {
        img = Image.asset(
          'assets/document.png',
          width: 60,
          height: 60,
          fit: BoxFit.cover,
        );
      }
    } else {
      img = Image.asset(
        'assets/document.png',
        width: 60,
        height: 60,
        fit: BoxFit.cover,
      );
    }
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(6),
        child: img,
      ),
    );
  }
}

class _MessageFileTile extends StatelessWidget {
  final ChatAttachment att;
  final Future<void> Function() onOpen;

  const _MessageFileTile({required this.att, required this.onOpen});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: const Color(0xFFF7F7F7),
      borderRadius: BorderRadius.circular(7),
      child: InkWell(
        borderRadius: BorderRadius.circular(7),
        onTap: () => onOpen(),
        child: Container(
          constraints: const BoxConstraints(maxWidth: 190),
          padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 6),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(7),
            border: Border.all(color: const Color(0x12000000)),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Image.asset('assets/document.png', width: 16, height: 16),
              const SizedBox(width: 5),
              Flexible(
                child: Text(
                  att.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 10.5,
                    color: _OhsdChatTheme.textSecondary,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _TypingDots extends StatefulWidget {
  const _TypingDots();

  @override
  State<_TypingDots> createState() => _TypingDotsState();
}

class _TypingDotsState extends State<_TypingDots>
    with SingleTickerProviderStateMixin {
  late final AnimationController _ctl;

  @override
  void initState() {
    super.initState();
    _ctl = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    )..repeat();
  }

  @override
  void dispose() {
    _ctl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _ctl,
      builder: (context, _) {
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: List.generate(3, (i) {
            final phase = (_ctl.value + i * 0.166) % 1.0;
            double y = 0;
            double op = 0.3;
            if (phase < 0.3) {
              final t = phase / 0.3;
              y = -4 * t;
              op = 0.3 + 0.7 * t;
            } else if (phase < 0.6) {
              final t = (phase - 0.3) / 0.3;
              y = -4 * (1 - t);
              op = 1 - 0.7 * t;
            }
            return Padding(
              padding: EdgeInsets.only(left: i == 0 ? 0 : 4),
              child: Transform.translate(
                offset: Offset(0, y),
                child: Opacity(
                  opacity: op.clamp(0.25, 1.0),
                  child: Container(
                    width: 4,
                    height: 4,
                    decoration: const BoxDecoration(
                      color: Color(0x40000000),
                      shape: BoxShape.circle,
                    ),
                  ),
                ),
              ),
            );
          }),
        );
      },
    );
  }
}

/// 账号余额（模拟页）
/// 服务协议（占位页，可后续换 WebView 或线上地址）
class _ServiceAgreementPage extends StatelessWidget {
  const _ServiceAgreementPage();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _OhsdChatTheme.pageBg,
      appBar: AppBar(
        backgroundColor: _OhsdChatTheme.bgSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        title: const Text(
          '服务协议',
          style: TextStyle(
            color: _OhsdChatTheme.textPrimary,
            fontSize: 17,
            fontWeight: FontWeight.w600,
          ),
        ),
        iconTheme: const IconThemeData(color: _OhsdChatTheme.textPrimary),
        bottom: const PreferredSize(
          preferredSize: Size.fromHeight(1),
          child: Divider(
            height: 1,
            thickness: 1,
            color: _OhsdChatTheme.borderHairline,
          ),
        ),
      ),
      body: const SingleChildScrollView(
        padding: EdgeInsets.all(20),
        child: Text(
          '此处为服务协议占位说明。正式环境请将法务提供的协议正文接入本页或使用 WebView 加载线上协议地址。',
          style: TextStyle(
            fontSize: 14,
            height: 1.65,
            color: _OhsdChatTheme.textSecondary,
          ),
        ),
      ),
    );
  }
}

class _SettingsPage extends StatelessWidget {
  const _SettingsPage();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: _OhsdChatTheme.pageBg,
      appBar: AppBar(
        backgroundColor: _OhsdChatTheme.bgSurface,
        elevation: 0,
        scrolledUnderElevation: 0,
        surfaceTintColor: Colors.transparent,
        title: const Text(
          '设置',
          style: TextStyle(
            color: _OhsdChatTheme.textPrimary,
            fontSize: 17,
            fontWeight: FontWeight.w600,
          ),
        ),
        iconTheme: const IconThemeData(color: _OhsdChatTheme.textPrimary),
        bottom: const PreferredSize(
          preferredSize: Size.fromHeight(1),
          child: Divider(
            height: 1,
            thickness: 1,
            color: _OhsdChatTheme.borderHairline,
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.symmetric(vertical: 8),
        children: [
          ListTile(
            leading: const Icon(
              Icons.notifications_none_outlined,
              color: _OhsdChatTheme.textSecondary,
            ),
            title: const Text(
              '通知',
              style: TextStyle(color: _OhsdChatTheme.textPrimary),
            ),
            subtitle: const Text(
              '推送与提醒',
              style: TextStyle(
                fontSize: 12,
                color: _OhsdChatTheme.textTertiary,
              ),
            ),
            onTap: () {
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(const SnackBar(content: Text('通知设置：敬请期待')));
            },
          ),
          ListTile(
            leading: const Icon(
              Icons.palette_outlined,
              color: _OhsdChatTheme.textSecondary,
            ),
            title: const Text(
              '外观',
              style: TextStyle(color: _OhsdChatTheme.textPrimary),
            ),
            subtitle: const Text(
              '主题与显示',
              style: TextStyle(
                fontSize: 12,
                color: _OhsdChatTheme.textTertiary,
              ),
            ),
            onTap: () {
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(const SnackBar(content: Text('外观设置：敬请期待')));
            },
          ),
          ListTile(
            leading: const Icon(
              Icons.menu_book_outlined,
              color: _OhsdChatTheme.textSecondary,
            ),
            title: const Text(
              'openHSD 教程',
              style: TextStyle(color: _OhsdChatTheme.textPrimary),
            ),
            subtitle: const Text(
              '插件下载与连接指引',
              style: TextStyle(
                fontSize: 12,
                color: _OhsdChatTheme.textTertiary,
              ),
            ),
            onTap: () => Navigator.of(context).pushNamed('/tutorial'),
          ),
          const Divider(height: 1, color: _OhsdChatTheme.borderHairline),
          ListTile(
            leading: const Icon(
              Icons.info_outline_rounded,
              color: _OhsdChatTheme.textSecondary,
            ),
            title: const Text(
              '关于',
              style: TextStyle(color: _OhsdChatTheme.textPrimary),
            ),
            subtitle: const Text(
              '会宝 网关客户端',
              style: TextStyle(
                fontSize: 12,
                color: _OhsdChatTheme.textTertiary,
              ),
            ),
            onTap: () {
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(const SnackBar(content: Text('会宝 网关客户端')));
            },
          ),
        ],
      ),
    );
  }
}

class ClipboardStatus {
  static void setClipboard(String text) {
    // Avoid pulling extra dependency; use Flutter built-in Clipboard API.
    // ignore: deprecated_member_use
    // ignore: unnecessary_null_comparison
    Clipboard.setData(ClipboardData(text: text));
  }
}
