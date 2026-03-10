/**
 * Claw 设备接口
 */

import { get } from '../utils/request.js'

/**
 * 获取用户的在线设备列表
 * @returns {Promise<{clawList: Array}>}
 */
export const getClawStatus = (userId) =>
  get(`/api/claw/status?userId=${userId}`)
