// ===== 用户登录态 store（user） =====
// 职责：集中管理登录态（token + 用户信息），支持从 sessionStorage 恢复，
// 提供登录写入 / 登出清理 / 角色与姓名便捷判断，供路由守卫、菜单与组件使用。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginResponse, RoleType } from '@/types'
import { clearToken, getToken, setToken } from '@/api/http'

// sessionStorage 中用户信息的存储键名
const USER_KEY = 'sms_user'

export const useUserStore = defineStore('user', () => {
  // token 从 sessionStorage 恢复（刷新页面不丢登录态），user 为登录响应缓存（含角色/姓名等）
  const token = ref<string>(getToken() || '')
  const user = ref<LoginResponse | null>(null)

  // 从 sessionStorage 恢复用户信息：JSON 解析失败视为未登录，防止脏数据导致角色判断异常
  function init() {
    const raw = sessionStorage.getItem(USER_KEY)
    if (raw) {
      try {
        user.value = JSON.parse(raw)
      } catch {
        user.value = null
      }
    }
  }

  // 登录成功：内存 + sessionStorage 双写（token 与用户信息），保证刷新页面后登录态可恢复
  function setLogin(data: LoginResponse) {
    token.value = data.token
    user.value = data
    setToken(data.token)
    sessionStorage.setItem(USER_KEY, JSON.stringify(data))
  }

  // 登出：清空内存与 sessionStorage 中的凭据和用户信息
  function logout() {
    token.value = ''
    user.value = null
    clearToken()
    sessionStorage.removeItem(USER_KEY)
  }

  // 便捷判断：是否有 token（登录态）/ 当前角色（路由守卫与菜单渲染依赖）/ 显示姓名
  const isLogin = () => !!token.value
  const role = () => user.value?.roleType as RoleType | undefined
  const displayName = () => user.value?.realName ?? ''

  init()
  return { token, user, setLogin, logout, isLogin, role, displayName }
})
