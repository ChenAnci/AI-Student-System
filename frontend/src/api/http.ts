import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { Result } from '@/types'

const TOKEN_KEY = 'sms_token'
// 使用 sessionStorage：关闭浏览器标签即失效，降低 XSS 窃取持久化凭据的暴露面
export const getToken = (): string | null => sessionStorage.getItem(TOKEN_KEY)
export const setToken = (token: string) => sessionStorage.setItem(TOKEN_KEY, token)
export const clearToken = () => sessionStorage.removeItem(TOKEN_KEY)

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse<Result>) => {
    // 文件下载：直接返回 Blob（若后端返回 JSON 错误则解析提示）
    if (response.config.responseType === 'blob') {
      const blob = response.data as unknown as Blob
      if (blob.type && blob.type.includes('json')) {
        return blob.text().then((text) => {
          const res = JSON.parse(text) as Result
          ElMessage.error(res.message || '下载失败')
          return Promise.reject(new Error(res.message))
        })
      }
      return blob as never
    }
    const res = response.data
    if (res.code === 200) {
      return res.data as never
    }
    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message))
  },
  (error) => {
    if (error.response?.status === 401) {
      clearToken()
      // 一并清除缓存的用户信息，避免与登录态不一致
      sessionStorage.removeItem('sms_user')
      ElMessage.error('登录已过期，请重新登录')
      router.push('/login')
    } else if (error.response?.data?.message) {
      ElMessage.error(error.response.data.message)
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default http

/** 触发浏览器下载 Blob 文件 */
export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

/** 生成带日期的下载文件名 */
export function datedFilename(prefix: string, ext = 'xlsx') {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${prefix}_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}.${ext}`
}
