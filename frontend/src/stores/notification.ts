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
    // 实时推送仅面向学生角色（服务端 WS 仅接受 STUDENT 认证），防御性拦截其它角色
    if (!token || !userStore.isLogin() || userStore.role() !== 'STUDENT') return
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
    // 安全：JWT 不在握手 URL query 中传输（避免进入访问日志），连接后通过首条 AUTH 消息认证
    ws = new WebSocket(WS_BASE)
    ws.onopen = () => {
      // 连接建立后立即发送 AUTH，服务端校验通过后回 AUTH_OK 才开始心跳
      ws?.send(JSON.stringify({ type: 'AUTH', token }))
    }
    ws.onmessage = (ev) => {
      try {
        const msg = JSON.parse(ev.data)
        if (msg.type === 'AUTH_OK') {
          connected.value = true
          retry = 0
          startHeartbeat()
          return
        }
        if (msg.type === 'PONG') return
        if (msg.type === 'NOTIFICATION') {
          unread.value += 1
          latest.value = msg.data
        }
      } catch {
        // 忽略非法帧
      }
    }
    ws.onclose = (ev) => {
      connected.value = false
      if (heartbeatTimer) {
        clearInterval(heartbeatTimer)
        heartbeatTimer = null
      }
      // 4401 = 认证失败/超时（token 无效或已过期），停止重连避免循环；其余情况按退避重连
      if (ev.code === 4401) return
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

  function startHeartbeat() {
    if (heartbeatTimer) return
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) ws.send('{"type":"PING"}')
    }, 25000)
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
