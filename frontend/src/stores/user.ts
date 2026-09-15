import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginResponse, RoleType } from '@/types'
import { clearToken, getToken, setToken } from '@/api/http'

const USER_KEY = 'sms_user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken() || '')
  const user = ref<LoginResponse | null>(null)

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

  function setLogin(data: LoginResponse) {
    token.value = data.token
    user.value = data
    setToken(data.token)
    sessionStorage.setItem(USER_KEY, JSON.stringify(data))
  }

  function logout() {
    token.value = ''
    user.value = null
    clearToken()
    sessionStorage.removeItem(USER_KEY)
  }

  const isLogin = () => !!token.value
  const role = () => user.value?.roleType as RoleType | undefined
  const displayName = () => user.value?.realName ?? ''

  init()
  return { token, user, setLogin, logout, isLogin, role, displayName }
})
