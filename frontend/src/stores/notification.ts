import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getToken } from '@/api/http'
import { unreadCount as fetchUnread } from '@/api/notification'
import { useUserStore } from '@/stores/user'

/** 站内通知 store：WebSocket 连接管理 + 未读数 + 心跳 + 断线重连 */
export const useNotificationStore = defineStore('notification', () => {
  const unread = ref(0)
  const connected = ref(false)
  const latest = ref<{ title: string; content: string; type: string } | null>(null)

  let ws: WebSocket | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let reconnectTimer: ReturnType<typeof setTimeout> | null = null
  let retry = 0

  const WS_BASE = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/notifications`

  function refreshUnread() {
    fetchUnread()
      .then((n) => {
        unread.value = n
      })
      .catch(() => {})
  }

  function connect() {
    const userStore = useUserStore()
    const token = getToken()
    if (!token || !userStore.isLogin()) return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
    ws = new WebSocket(`${WS_BASE}?token=${encodeURIComponent(token)}`)
    ws.onopen = () => {
      connected.value = true
      retry = 0
      heartbeatTimer = setInterval(() => {
        if (ws?.readyState === WebSocket.OPEN) ws.send('{"type":"PING"}')
      }, 25000)
    }
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data)
        if (msg.type === 'PONG') return
        if (msg.type === 'NOTIFICATION') {
          unread.value += 1
          latest.value = msg.data
        }
      } catch {
        // 忽略非法帧
      }
    }
    ws.onclose = () => {
      connected.value = false
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
        heartbeatTimer = null
      }
      scheduleReconnect()
    }
    ws.onerror = () => {
      try {
        ws?.close()
      } catch {
        // no-op
      }
    }
  }

  function scheduleReconnect() {
    if (document.hidden) return
    const userStore = useUserStore()
    if (!userStore.isLogin()) return
    const delay = Math.min(1000 * 2 ** retry, 30000)
    retry += 1
    reconnectTimer = setTimeout(() => connect(), delay)
  }

  function disconnect() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
    if (ws) {
      ws.onclose = null
      ws.close()
      ws = null
    }
    connected.value = false
  }

  return { unread, connected, latest, connect, disconnect, refreshUnread }
})
