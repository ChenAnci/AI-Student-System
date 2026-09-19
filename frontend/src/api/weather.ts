// ===== 实时天气接口（weather） =====
// 职责：封装后端 /api/weather 代理接口；AK 在服务端，前端不接触密钥。
import http from './http'
import type { WeatherInfo } from '@/types'

/** 获取上海实时天气（后端降级时 data 为 null） */
export function getWeather() {
  return http.get('/weather') as Promise<WeatherInfo>
}
