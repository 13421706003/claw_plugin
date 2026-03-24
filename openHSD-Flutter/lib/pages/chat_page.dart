import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import 'package:provider/provider.dart';
import 'package:flutter_svg/flutter_svg.dart';
import 'package:flutter/services.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:record/record.dart';

import '../controllers/auth_controller.dart';
import '../controllers/chat_controller.dart';
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
    final b64 = _dataUriToBase64(att.dataUri ?? '');
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
                                        'OPENHSD',
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
                          onTap: () async {
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
                            return _MessageBubble(msg: msg);
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
                                  controller: _inputCtl,
                                  focusNode: _inputFocus,
                                  maxLength: 500,
                                  maxLines: 4,
                                  minLines: 1,
                                  keyboardType: TextInputType.multiline,
                                  textInputAction: TextInputAction.send,
                                  onSubmitted: (_) => _sendMessage(),
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
                                    onTap: _pickFiles,
                                  ),
                                  const SizedBox(width: 6),
                                  _ChatInputSideButton(
                                    icon: Icons.mic_none_outlined,
                                    label: '语音',
                                    active: _voiceUiEnabled,
                                    onTap: () => setState(() {
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
                          'OPENHSD',
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
                  _drawerNavRow(
                    icon: Icons.person_outline_rounded,
                    title: '个人中心',
                    onTap: () {
                      Navigator.of(context).pop();
                      Navigator.of(context).push(
                        MaterialPageRoute<void>(
                          builder: (_) => _ProfileCenterPage(auth: auth, chat: chat),
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
                                            ScaffoldMessenger.of(context)
                                                .showSnackBar(
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
  final VoidCallback onTap;
  final Widget child;

  const _ToolIconButton({
    required this.tooltip,
    required this.onTap,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
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
            child: child,
          ),
        ),
      ),
    );
  }
}

class _ChatInputSideButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final bool active;

  const _ChatInputSideButton({
    required this.icon,
    required this.label,
    required this.onTap,
    this.active = false,
  });

  @override
  Widget build(BuildContext context) {
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
              Icon(icon, size: 18, color: fg),
              const SizedBox(width: 5),
              Text(label, style: TextStyle(fontSize: 11, color: fg, height: 1)),
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

  const _ComposerAttachmentTile({
    required this.att,
    required this.imageProvider,
    required this.onRemove,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      clipBehavior: Clip.none,
      children: [
        if (att.isImage)
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: Image(
              image: imageProvider,
              width: 48,
              height: 48,
              fit: BoxFit.cover,
            ),
          )
        else
          Container(
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
      child: const Icon(
        Icons.person,
        size: 14,
        color: Color(0xFF5D6B86),
      ),
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
      bubbleChild = MarkdownBody(
        data: msg.content,
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

    final bubbleColumn = Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: isUser
          ? CrossAxisAlignment.end
          : CrossAxisAlignment.start,
      children: [
        if (isUser && msg.attachments.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Wrap(
              spacing: 3,
              runSpacing: 3,
              alignment: WrapAlignment.end,
              children: msg.attachments
                  .where((a) => a.isImage && a.dataUri != null)
                  .take(6)
                  .map(
                    (att) => ClipRRect(
                      borderRadius: BorderRadius.circular(6),
                      child: Image.memory(
                        base64Decode(att.dataUri!.split(',').last),
                        width: 60,
                        height: 60,
                        fit: BoxFit.cover,
                      ),
                    ),
                  )
                  .toList(),
            ),
          ),
        Container(
          width: isUser ? null : double.infinity,
          padding: const EdgeInsets.symmetric(horizontal: 11, vertical: 9),
          constraints: BoxConstraints(
            maxWidth: MediaQuery.sizeOf(context).width * 0.75,
          ),
          decoration: bubbleDecoration,
          child: bubbleChild,
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
              'OPENHSD 网关客户端',
              style: TextStyle(
                fontSize: 12,
                color: _OhsdChatTheme.textTertiary,
              ),
            ),
            onTap: () {
              ScaffoldMessenger.of(
                context,
              ).showSnackBar(const SnackBar(content: Text('OPENHSD 网关客户端')));
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
