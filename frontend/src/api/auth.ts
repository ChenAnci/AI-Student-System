import http from './http'
import type { LoginResponse } from '@/types'

export function login(data: { username: string; password: string }) {
  return http.post('/auth/login', data) as Promise<LoginResponse>
}
