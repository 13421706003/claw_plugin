import 'dart:convert';

String extractText(dynamic content) {
  if (content == null) return '';

  if (content is String) {
    // Sometimes backend/chunks send JSON-stringified blocks.
    try {
      final decoded = jsonDecode(content);
      if (decoded is List) return parseBlocks(decoded);
    } catch (_) {}
    return content;
  }

  if (content is List) {
    return parseBlocks(content);
  }

  return '';
}

String parseBlocks(List blocks) {
  return blocks.map((e) {
    if (e is! Map) return '';
    final type = e['type']?.toString();

    switch (type) {
      case 'text':
        return (e['text'] ?? '').toString();
      case 'image':
        final url = e['url']?.toString();
        return (url == null || url.isEmpty) ? '' : '![image]($url)';
      case 'code':
        final lang = e['language']?.toString() ?? '';
        final text = e['text']?.toString() ?? '';
        return '```$lang\n$text\n```';
      default:
        return (e['text'] ?? '').toString();
    }
  }).where((s) => s.isNotEmpty).join('\n\n');
}

