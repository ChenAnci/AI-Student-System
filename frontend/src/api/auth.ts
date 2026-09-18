// ===== 认证相关接口（auth） =====
// 职责：封装登录等认证接口调用。
import http from './http'
import type { LoginResponse } from '@/types'

/** 调用 POST /api/auth/login：账号密码登录，成功后返回 token 与用户信息 */
export function login(data: { username: string; password: string }) {
  return http.post('/auth/login', data) as Promise<LoginResponse>
}
