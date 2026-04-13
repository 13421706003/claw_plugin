import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

class TutorialPage extends StatelessWidget {
  const TutorialPage({super.key});

  static const String routeName = '/tutorial';

  static const String _downloadUrl =
      'http://huashidai1.com/oss/hsdclaw/hsdclaw_plugin/openHSD-plugin.zip';

  static const String _tutorialImageBase = 'https://www.huashidai1.com/tutorial';

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('openHSD 使用教程'),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 28),
        children: [
          Text(
            '使用教程',
            style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                  fontWeight: FontWeight.w700,
                ),
          ),
          const SizedBox(height: 12),
          _StepCard(
            title: '1. 下载插件',
            body: [
              const Text('下载完成后，将压缩包解压到任意目录。'),
              const SizedBox(height: 8),
              Wrap(
                crossAxisAlignment: WrapCrossAlignment.center,
                spacing: 8,
                children: [
                  const Text('下载地址：'),
                  TextButton(
                    onPressed: () => _launchExternal(_downloadUrl),
                    child: const Text('点击下载 openHSD-plugin.zip'),
                  ),
                ],
              ),
            ],
          ),
          _StepCard(
            title: '2. 访问网站，进行登录注册',
            body: const [
              Text('打开浏览器，访问网站地址，进入登录页面。'),
              SizedBox(height: 6),
              Text('如果没有账号，点击“注册”按钮进行账号注册；已有账号可直接输入用户名和密码登录。'),
            ],
            imageUrls: const ['$_tutorialImageBase/connect.png'],
          ),
          _StepCard(
            title: '3. 启动插件与配置',
            body: const [
              Text('打开插件文件夹，双击 start.bat 运行插件。'),
              SizedBox(height: 6),
              Text('首次启动会提示：“是否重新输入token？”。'),
              SizedBox(height: 6),
              Text('输入 yes 或 y：重新配置 Token'),
              Text('输入 no 或 n：使用上次保存的 Token'),
            ],
            imageUrls: const ['$_tutorialImageBase/plugin-start.png'],
          ),
          _StepCard(
            title: '4. 获取Token',
            body: const [
              Text('登录成功后，进入主界面。'),
              SizedBox(height: 6),
              Text('点击右侧导航栏底部的用户头像，弹出用户面板。'),
              SizedBox(height: 6),
              Text('面板中显示“连接 Token”，点击“复制 Token”按钮即可将 Token 复制到剪贴板。'),
            ],
            imageUrls: const ['$_tutorialImageBase/connect.png'],
          ),
          _StepCard(
            title: '5. 输入Token连接',
            body: const [
              Text('将复制的 Token 粘贴到插件终端中，按 Enter 键确认。'),
              SizedBox(height: 6),
              Text('如果 Token 正确且网络正常，终端会显示“连接成功”提示。'),
              SizedBox(height: 6),
              Text('此时插件已与服务器建立 WebSocket 连接，可以正常使用聊天功能。'),
            ],
            imageUrls: const ['$_tutorialImageBase/connect-token.png'],
          ),
          _StepCard(
            title: '6. 连接失败情况',
            body: const [
              Text('如果出现以下情况，连接可能失败：'),
              SizedBox(height: 6),
              _BulletList(
                items: [
                  'Token 已过期或无效',
                  '网络连接异常',
                  '服务器未响应',
                ],
              ),
              SizedBox(height: 6),
              Text('请检查 Token 是否正确，或重新登录网站获取新的 Token。'),
            ],
            imageUrls: const [
              '$_tutorialImageBase/connect-false1.png',
              '$_tutorialImageBase/connect-false2.png',
            ],
          ),
          const SizedBox(height: 8),
          Container(
            decoration: BoxDecoration(
              color: colorScheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(12),
            ),
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Icon(Icons.tips_and_updates_outlined, color: colorScheme.primary),
                const SizedBox(width: 10),
                const Expanded(
                  child: Text(
                    '提示：如果图片无法加载，请检查网络或切换到可访问 www.huashidai1.com 的环境。',
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _launchExternal(String url) async {
    final uri = Uri.parse(url);
    final ok = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!ok) {
      // ignore: only_throw_errors
      throw 'Could not launch $url';
    }
  }
}

class _StepCard extends StatelessWidget {
  const _StepCard({
    required this.title,
    required this.body,
    this.imageUrls = const [],
  });

  final String title;
  final List<Widget> body;
  final List<String> imageUrls;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(top: 14),
      elevation: 0,
      color: Theme.of(context).colorScheme.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(14),
        side: BorderSide(
          color: Theme.of(context).colorScheme.outlineVariant,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 14, 14, 14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
            ),
            const SizedBox(height: 10),
            ...body,
            if (imageUrls.isNotEmpty) ...[
              const SizedBox(height: 12),
              _TutorialImages(imageUrls: imageUrls),
            ],
          ],
        ),
      ),
    );
  }
}

class _TutorialImages extends StatelessWidget {
  const _TutorialImages({required this.imageUrls});

  final List<String> imageUrls;

  @override
  Widget build(BuildContext context) {
    final isMulti = imageUrls.length > 1;
    final width = MediaQuery.sizeOf(context).width;
    final itemWidth = isMulti ? (width - 16 * 2 - 10) / 2 : width - 16 * 2;

    return Wrap(
      spacing: 10,
      runSpacing: 10,
      children: [
        for (final url in imageUrls)
          _ImageTile(
            url: url,
            width: itemWidth.clamp(160, 520),
          ),
      ],
    );
  }
}

class _ImageTile extends StatelessWidget {
  const _ImageTile({required this.url, required this.width});

  final String url;
  final double width;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: () => showDialog<void>(
        context: context,
        builder: (_) => _ImagePreviewDialog(url: url),
      ),
      borderRadius: BorderRadius.circular(12),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Container(
          width: width,
          decoration: BoxDecoration(
            border: Border.all(color: Theme.of(context).colorScheme.outlineVariant),
            borderRadius: BorderRadius.circular(12),
          ),
          child: AspectRatio(
            aspectRatio: 16 / 10,
            child: Image.network(
              url,
              fit: BoxFit.cover,
              errorBuilder: (context, error, stackTrace) => Container(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                alignment: Alignment.center,
                padding: const EdgeInsets.all(12),
                child: const Text('图片加载失败'),
              ),
              loadingBuilder: (context, child, progress) {
                if (progress == null) return child;
                final value = progress.expectedTotalBytes == null
                    ? null
                    : progress.cumulativeBytesLoaded / progress.expectedTotalBytes!;
                return Container(
                  color: Theme.of(context).colorScheme.surfaceContainerHighest,
                  alignment: Alignment.center,
                  child: SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2, value: value),
                  ),
                );
              },
            ),
          ),
        ),
      ),
    );
  }
}

class _ImagePreviewDialog extends StatelessWidget {
  const _ImagePreviewDialog({required this.url});

  final String url;

  @override
  Widget build(BuildContext context) {
    return Dialog(
      insetPadding: const EdgeInsets.all(16),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: Stack(
          children: [
            Positioned.fill(
              child: InteractiveViewer(
                minScale: 0.8,
                maxScale: 4,
                child: Image.network(
                  url,
                  fit: BoxFit.contain,
                  errorBuilder: (context, error, stackTrace) => Container(
                    color: Theme.of(context).colorScheme.surfaceContainerHighest,
                    alignment: Alignment.center,
                    child: const Text('图片加载失败'),
                  ),
                ),
              ),
            ),
            Positioned(
              top: 8,
              right: 8,
              child: IconButton(
                onPressed: () => Navigator.of(context).pop(),
                icon: const Icon(Icons.close),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _BulletList extends StatelessWidget {
  const _BulletList({required this.items});

  final List<String> items;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (final item in items)
          Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('•  '),
                Expanded(child: Text(item)),
              ],
            ),
          ),
      ],
    );
  }
}

