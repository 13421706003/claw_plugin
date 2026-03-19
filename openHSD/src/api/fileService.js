import { API_BASE } from './request.js'

/** 单文件最大 50MB（与后端对齐） */
export const MAX_FILE_SIZE = 50 * 1024 * 1024

function getToken() {
  return localStorage.getItem('openhsd_token') || sessionStorage.getItem('openhsd_token') || ''
}

/** 允许的 MIME 类型 */
export const ALLOWED_MIME_TYPES = [
  'image/jpeg', 'image/png', 'image/gif', 'image/webp', 'image/bmp',
  'application/pdf',
  'application/msword',
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-excel',
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'application/vnd.ms-powerpoint',
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'text/plain',
  'text/markdown',
  'text/csv',
  'application/zip',
  'application/gzip',
  'application/json',
]

/**
 * 上传文件列表到后端 MinIO
 *
 * @param {File[]} files    文件数组
 * @param {string} userId   用户 ID
 * @param {string} clawId   设备 ID
 * @returns {Promise<Array>} 上传成功的附件列表
 *   每项: { objectKey, url, name, type, size }
 */
export async function uploadFiles(files, userId, clawId) {
  const formData = new FormData()
  formData.append('userId', userId)
  formData.append('clawId', clawId)
  for (const file of files) {
    formData.append('files', file)
  }

  const token = getToken()
  const headers = {}
  
  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }
  
  const res = await fetch(`${API_BASE}/file/upload`, {
    method: 'POST',
    body: formData,
    headers,
  })

  if (!res.ok) {
    const text = await res.text()
    throw new Error(`上传失败 (${res.status}): ${text}`)
  }

  const data = await res.json()
  if (!data.success) {
    throw new Error(data.message || '上传失败')
  }

  console.log('[fileService] 上传结果：', JSON.stringify(data.files))
  return data.files // [{ objectKey, url, name, type, size }]
}

/**
 * 校验单个文件是否符合要求
 * @returns {{ valid: boolean, reason?: string }}
 */
export function validateFile(file) {
  if (file.size > MAX_FILE_SIZE) {
    return { valid: false, reason: `文件超过 50MB 限制（当前 ${formatSize(file.size)}）` }
  }
  const mime = file.type || ''
  const allowed =
    ALLOWED_MIME_TYPES.includes(mime) ||
    mime.startsWith('image/') ||
    mime.startsWith('text/')
  if (!allowed) {
    return { valid: false, reason: `不支持的文件类型：${mime || '未知'}` }
  }
  return { valid: true }
}

/**
 * 格式化文件大小
 */
export function formatSize(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
