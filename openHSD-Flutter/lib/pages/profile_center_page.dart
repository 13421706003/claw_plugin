part of 'chat_page.dart';

class _ProfileCenterPage extends StatefulWidget {
  final AuthController auth;
  final ChatController chat;

  const _ProfileCenterPage({required this.auth, required this.chat});

  @override
  State<_ProfileCenterPage> createState() => _ProfileCenterPageState();
}

class _ProfileCenterPageState extends State<_ProfileCenterPage> {
  bool _tokenCopied = false;

  String _formatHeartbeat(int ts) {
    if (ts <= 0) return '未知';
    final diff = DateTime.now().millisecondsSinceEpoch - ts;
    if (diff < 60000) return '${diff ~/ 1000}s前';
    return '${diff ~/ 60000}min前';
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: Listenable.merge([widget.auth, widget.chat]),
      builder: (context, _) {
        final userInitial = (widget.auth.user?.username.isNotEmpty ?? false)
            ? widget.auth.user!.username[0].toUpperCase()
            : 'U';
        return Scaffold(
          backgroundColor: _OhsdChatTheme.pageBg,
          appBar: AppBar(
            backgroundColor: _OhsdChatTheme.bgSurface,
            elevation: 0,
            scrolledUnderElevation: 0,
            surfaceTintColor: Colors.transparent,
            title: const Text(
              '个人中心',
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
            padding: const EdgeInsets.fromLTRB(14, 14, 14, 20),
            children: [
              Container(
                padding: const EdgeInsets.fromLTRB(14, 14, 14, 12),
                decoration: BoxDecoration(
                  color: _OhsdChatTheme.bgSurface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: _OhsdChatTheme.borderHairline),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 42,
                      height: 42,
                      decoration: const BoxDecoration(
                        shape: BoxShape.circle,
                        gradient: LinearGradient(
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                          colors: [_OhsdChatTheme.error, _OhsdChatTheme.dangerDeep],
                        ),
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        userInitial,
                        style: const TextStyle(
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                        ),
                      ),
                    ),
                    const SizedBox(width: 10),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            widget.auth.user?.username ?? '',
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                              color: _OhsdChatTheme.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'ID: ${widget.auth.user?.userId ?? ''}',
                            style: const TextStyle(
                              fontSize: 12,
                              color: _OhsdChatTheme.textTertiary,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: _OhsdChatTheme.bgSurface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: _OhsdChatTheme.borderHairline),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'TOKEN',
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: _OhsdChatTheme.textTertiary,
                        letterSpacing: 0.4,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      constraints: const BoxConstraints(maxHeight: 80),
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 8,
                      ),
                      decoration: BoxDecoration(
                        color: _OhsdChatTheme.bgMuted,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: SingleChildScrollView(
                        child: SelectableText(
                          widget.auth.token,
                          style: const TextStyle(
                            fontSize: 10,
                            color: _OhsdChatTheme.textSecondary,
                            fontFamily: 'monospace',
                            height: 1.5,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),
                    _actionButton(
                      icon: Icons.copy_rounded,
                      text: _tokenCopied ? '已复制' : '复制 Token',
                      onTap: () {
                        ClipboardStatus.setClipboard(widget.auth.token);
                        setState(() => _tokenCopied = true);
                        Future<void>.delayed(
                          const Duration(milliseconds: 1500),
                          () {
                            if (!mounted) return;
                            setState(() => _tokenCopied = false);
                          },
                        );
                      },
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),
              Container(
                decoration: BoxDecoration(
                  color: _OhsdChatTheme.bgSurface,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: _OhsdChatTheme.borderHairline),
                ),
                child: Column(
                  children: [
                    Padding(
                      padding: const EdgeInsets.fromLTRB(14, 12, 8, 8),
                      child: Row(
                        children: [
                          const Text(
                            '在线设备',
                            style: TextStyle(
                              color: _OhsdChatTheme.textPrimary,
                              fontSize: 15,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          const Spacer(),
                          TextButton(
                            onPressed: widget.chat.loadingDevices
                                ? null
                                : () async {
                                    final ok = await widget.chat
                                        .fetchClawStatus();
                                    if (!context.mounted) return;
                                    final msg = ok
                                        ? '刷新完成：在线设备 ${widget.chat.clawList.length} 台'
                                        : '刷新失败：${widget.chat.lastDeviceRefreshError ?? '请检查网络/后端'}';
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(content: Text(msg)),
                                    );
                                  },
                            child: Text(widget.chat.loadingDevices ? '刷新中' : '刷新'),
                          ),
                        ],
                      ),
                    ),
                    const Divider(height: 1, color: _OhsdChatTheme.borderHairline),
                    if (widget.chat.clawList.isEmpty)
                      Padding(
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        child: Text(
                          '暂无在线设备',
                          style: TextStyle(
                            fontSize: 12,
                            color: _OhsdChatTheme.textTertiary,
                          ),
                        ),
                      )
                    else
                      ...widget.chat.clawList.map(
                        (d) => ListTile(
                          dense: true,
                          leading: const Icon(
                            Icons.memory_rounded,
                            size: 18,
                            color: _OhsdChatTheme.success,
                          ),
                          title: Text(
                            d.clawId,
                            style: const TextStyle(
                              color: _OhsdChatTheme.textPrimary,
                              fontSize: 13,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          subtitle: Text(
                            _formatHeartbeat(d.lastHeartbeat),
                            style: const TextStyle(
                              color: _OhsdChatTheme.textTertiary,
                              fontSize: 11,
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              Material(
                color: _OhsdChatTheme.dangerFaintBg,
                borderRadius: BorderRadius.circular(10),
                child: InkWell(
                  borderRadius: BorderRadius.circular(10),
                  onTap: () async {
                    widget.chat.ws.disconnect();
                    await widget.auth.logout();
                    if (!context.mounted) return;
                    Navigator.of(context).pushReplacementNamed('/login');
                  },
                  child: const SizedBox(
                    height: 44,
                    child: Center(
                      child: Text(
                        '退出登录',
                        style: TextStyle(
                          color: _OhsdChatTheme.error,
                          fontWeight: FontWeight.w600,
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
  }

  Widget _actionButton({
    required IconData icon,
    required String text,
    required VoidCallback onTap,
  }) {
    return Material(
      color: _OhsdChatTheme.bgSurface,
      borderRadius: BorderRadius.circular(10),
      child: InkWell(
        borderRadius: BorderRadius.circular(10),
        onTap: onTap,
        child: Container(
          height: 42,
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(10),
            border: Border.all(color: _OhsdChatTheme.borderHairline),
          ),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 18, color: _OhsdChatTheme.primary),
              const SizedBox(width: 6),
              Text(
                text,
                style: const TextStyle(
                  color: _OhsdChatTheme.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
