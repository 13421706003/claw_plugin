/**
 * 语音识别：经 openHSD 后端代理阿里云百炼（DashScope）
 * https://www.alibabacloud.com/help/zh/model-studio/real-time-speech-recognition
 */

import { storage } from '../utils/storage.js'
import { BASE_URL } from '../utils/request.js'

/**
 * 上传本地音频文件，同步返回识别文本
 * @param {string} filePath uni 临时文件路径
 * @param {number} [durationMs] 可选，时长毫秒（与后端兼容）
 */
export function transcribeAudioFile(filePath, durationMs) {
  const token = storage.getToken()
  return new Promise((resolve, reject) => {
    const formData = {}
    if (durationMs != null && durationMs > 0) {
      formData.durationMs = String(durationMs)
    }
    uni.uploadFile({
      url: `${BASE_URL}/api/speech/bailian/transcribe`,
      filePath,
      name: 'file',
      formData,
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const data = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
          if (data.success && data.text != null) {
            resolve(String(data.text))
          } else {
            reject(new Error(data.message || '识别失败'))
          }
        } catch (e) {
          reject(new Error('解析响应失败'))
        }
      },
      fail: (err) => reject(new Error(err.errMsg || '上传失败')),
    })
  })
}
