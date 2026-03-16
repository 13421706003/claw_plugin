import { PDFParse } from 'pdf-parse';
import mammoth from 'mammoth';
import XLSX from 'xlsx';
import { Buffer } from 'node:buffer';

/**
 * 从 Buffer 中提取文本内容
 * 根据 MIME 类型自动选择解析方式
 *
 * @param {Buffer}  buffer   文件二进制
 * @param {string}  mimeType MIME 类型
 * @param {string}  fileName 文件名（用于扩展名推断）
 * @returns {Promise<string>} 提取的文本内容
 */
export async function extractText(buffer, mimeType, fileName = '') {
  const mime = (mimeType || '').toLowerCase();
  const ext  = extFromName(fileName);

  // ── PDF ────────────────────────────────────────────────────────
  if (mime === 'application/pdf' || ext === 'pdf') {
    return await extractPdf(buffer);
  }

  // ── Word (.docx) ──────────────────────────────────────────────
  if (mime === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      || ext === 'docx') {
    return await extractDocx(buffer);
  }

  // ── Word (.doc) 旧格式 ────────────────────────────────────────
  if (mime === 'application/msword' || ext === 'doc') {
    return extractLegacyDoc(buffer);
  }

  // ── Excel (.xlsx / .xls) ──────────────────────────────────────
  if (mime === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      || mime === 'application/vnd.ms-excel'
      || ext === 'xlsx' || ext === 'xls') {
    return extractExcel(buffer);
  }

  // ── PowerPoint (.pptx) ────────────────────────────────────────
  if (mime === 'application/vnd.openxmlformats-officedocument.presentationml.presentation'
      || mime === 'application/vnd.ms-powerpoint'
      || ext === 'pptx' || ext === 'ppt') {
    return extractPptx(buffer);
  }

  // ── 纯文本类：TXT / MD / CSV / JSON / 代码 ───────────────────
  if (mime.startsWith('text/')
      || mime === 'application/json'
      || ['txt', 'md', 'csv', 'json', 'log', 'xml', 'html', 'yml', 'yaml', 'toml', 'ini', 'cfg'].includes(ext)) {
    return buffer.toString('utf-8');
  }

  // ── 未知格式：尝试 UTF-8 解码，检测可读性 ─────────────────────
  const text = buffer.toString('utf-8');
  const printable = text.replace(/[^\x20-\x7E\r\n\t\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]/g, '');
  if (printable.length / text.length > 0.7) {
    return text;
  }
  return `[无法提取文本内容，文件格式：${mimeType}]`;
}


// ═══════════════════════════════════════════════════════════════════
//  各格式提取实现
// ═══════════════════════════════════════════════════════════════════

/**
 * PDF → 文本
 */
async function extractPdf(buffer) {
  let pdf;
  try {
    pdf = new PDFParse({ data: buffer });
    const result = await pdf.getText({ max: 200 });
    const text = result.text?.trim();
    if (text) {
      console.log(`[fileExtractor] PDF 解析成功：${result.total} 页，${text.length} 字符`);
      return text;
    }
    return '[PDF 内容为空，可能是扫描件或纯图片 PDF]';
  } catch (e) {
    console.error('[fileExtractor] PDF 解析失败：', e.message);
    return `[PDF 解析失败：${e.message}]`;
  } finally {
    if (pdf) await pdf.destroy().catch(() => {});
  }
}

/**
 * Word (.docx) → 文本
 */
async function extractDocx(buffer) {
  try {
    const result = await mammoth.extractRawText({ buffer });
    const text = result.value?.trim();
    if (text) {
      console.log(`[fileExtractor] DOCX 解析成功：${text.length} 字符`);
      return text;
    }
    return '[DOCX 内容为空]';
  } catch (e) {
    console.error('[fileExtractor] DOCX 解析失败：', e.message);
    return `[DOCX 解析失败：${e.message}]`;
  }
}

/**
 * Word (.doc) 旧格式 → 尝试提取可读文本
 * .doc 是 OLE2 二进制格式，没有轻量级纯 JS 库能完美解析
 * 这里做简易提取：跳过控制字符，提取连续可读文本段
 */
function extractLegacyDoc(buffer) {
  try {
    const raw = buffer.toString('latin1');
    // 提取连续可读字符段（ASCII + 常见符号）
    const chunks = [];
    let current = '';
    for (let i = 0; i < raw.length; i++) {
      const code = raw.charCodeAt(i);
      if ((code >= 0x20 && code <= 0x7e) || code === 0x0a || code === 0x0d || code === 0x09) {
        current += raw[i];
      } else {
        if (current.trim().length > 10) {
          chunks.push(current.trim());
        }
        current = '';
      }
    }
    if (current.trim().length > 10) chunks.push(current.trim());

    const text = chunks.join('\n');
    if (text.length > 50) {
      console.log(`[fileExtractor] DOC 简易提取：${text.length} 字符`);
      return text;
    }
    return '[.doc 旧格式提取失败，建议转换为 .docx]';
  } catch (e) {
    return `[DOC 解析失败：${e.message}]`;
  }
}

/**
 * Excel (.xlsx / .xls) → CSV 格式文本
 */
function extractExcel(buffer) {
  try {
    const workbook = XLSX.read(buffer, { type: 'buffer' });
    const sheets = [];

    for (const sheetName of workbook.SheetNames) {
      const sheet = workbook.Sheets[sheetName];
      const csv = XLSX.utils.sheet_to_csv(sheet, { blankrows: false });
      if (csv.trim()) {
        sheets.push(`=== Sheet: ${sheetName} ===\n${csv}`);
      }
    }

    const text = sheets.join('\n\n');
    if (text.trim()) {
      console.log(`[fileExtractor] Excel 解析成功：${workbook.SheetNames.length} 个 sheet，${text.length} 字符`);
      return text;
    }
    return '[Excel 内容为空]';
  } catch (e) {
    console.error('[fileExtractor] Excel 解析失败：', e.message);
    return `[Excel 解析失败：${e.message}]`;
  }
}

/**
 * PowerPoint (.pptx) → 文本
 * .pptx 是 ZIP 包，内部是 XML
 * 用 xlsx 库的 ZIP 解压能力读取 slide XML，提取文本节点
 */
function extractPptx(buffer) {
  try {
    // pptx 本质是 zip，用 xlsx 的 cfb/zip 读取
    const zip = XLSX.read(buffer, { type: 'buffer', bookSheets: true });

    // 直接用 JSZip 兼容方式读取 zip 条目不太方便
    // 另一种方式：手动解压 pptx zip 结构
    const AdmZip = createAdmZipFromBuffer(buffer);
    if (!AdmZip) {
      return extractPptxFallback(buffer);
    }
    return AdmZip;
  } catch (e) {
    console.error('[fileExtractor] PPTX 解析失败：', e.message);
    return extractPptxFallback(buffer);
  }
}

/**
 * PPTX 简易提取：从二进制中查找 XML 文本节点
 */
function extractPptxFallback(buffer) {
  try {
    const raw = buffer.toString('utf-8');
    // pptx 内部 XML 中文本在 <a:t>...</a:t> 标签里
    const textRegex = /<a:t>([^<]*)<\/a:t>/g;
    const texts = [];
    let match;
    while ((match = textRegex.exec(raw)) !== null) {
      const t = match[1].trim();
      if (t) texts.push(t);
    }
    if (texts.length > 0) {
      const text = texts.join(' ');
      console.log(`[fileExtractor] PPTX 简易提取：${texts.length} 个文本片段，${text.length} 字符`);
      return text;
    }
    return '[PPTX 内容为空或无法提取]';
  } catch (e) {
    return `[PPTX 解析失败：${e.message}]`;
  }
}

/**
 * 尝试用内置 zip 解压读取 pptx slide XML
 */
function createAdmZipFromBuffer(buffer) {
  try {
    // 用 XLSX 的底层 CFB 工具读取 zip
    // pptx 中 slide 文件在 ppt/slides/slide*.xml
    const raw = buffer.toString('binary');

    // 查找所有 slide XML 内容
    const slideTexts = [];
    const slidePattern = /ppt\/slides\/slide\d+\.xml/g;

    // 直接在 buffer 中搜索 <a:t> 标签
    const utf8 = buffer.toString('utf-8');
    const regex = /<a:t>([^<]+)<\/a:t>/g;
    const texts = [];
    let m;
    while ((m = regex.exec(utf8)) !== null) {
      const t = m[1].trim();
      if (t) texts.push(t);
    }

    if (texts.length > 0) {
      return texts.join(' ');
    }
    return null;
  } catch {
    return null;
  }
}


// ═══════════════════════════════════════════════════════════════════
//  工具函数
// ═══════════════════════════════════════════════════════════════════

function extFromName(name) {
  if (!name || !name.includes('.')) return '';
  return name.split('.').pop().toLowerCase();
}
