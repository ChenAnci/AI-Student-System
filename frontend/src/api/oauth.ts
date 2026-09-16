import http from './http'
import type { LoginResponse } from '@/types'

/** 获取 GitHub 授权跳转地址 */
export function getGithubAuthorizeUrl() {
  return http.get('/oauth/github/authorize') as Promise<{ url: string }>
}

/** 绑定 GitHub 到现有系统账号并登录 */
export function bindGithub(data: { username: string; password: string; providerUid: string }) {
  return http.post('/oauth/github/bind', data) as Promise<LoginResponse>
}

/** 用一次性授权码换取登录态（回调页调用，避免 JWT 暴露在 URL） */
export function exchangeGithub(data: { authCode: string }) {
  return http.post('/oauth/github/exchange', data) as Promise<LoginResponse>
}
