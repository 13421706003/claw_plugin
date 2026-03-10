/**
 * HTTP 请求封装（uni.request）
 * 自动注入 Authorization Token
 * 统一返回 data，抛出错误
 */

import { storage } from './storage.js'

const BASE_URL = 'http://192.168.110.129:9081'

/**
 * 基础请求
 * @param {string} url       相对路径，如 /api/auth/login
 * @param {string} method    GET | POST | PUT | DELETE
 * @param {object} data      请求体（POST/PUT）或 query（GET）
 * @param {object} headers   额外请求头
 */
export const request = (url, method = 'GET', data = {}, headers = {}) => {
  return new Promise((resolve, reject) => {
    const token = storage.getToken()

    uni.request({
      url: BASE_URL + url,
      method,
      data,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...headers,
      },
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
        } else {
          const msg = res.data?.message || `HTTP ${res.statusCode}`
          reject(new Error(msg))
        }
      },
      fail: (err) => {
        reject(new Error(err.errMsg || '网络请求失败'))
      }
    })
  })
}

export const get  = (url, data, headers)       => request(url, 'GET',    data, headers)
export const post = (url, data, headers)       => request(url, 'POST',   data, headers)
export const del  = (url, data, headers)       => request(url, 'DELETE', data, headers)
