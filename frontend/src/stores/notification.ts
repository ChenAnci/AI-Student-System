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

  // WebSocket 地址：协议随页面 http/https 自动切换为 ws/wss（避免混合内容被浏览器拦截）。
  // 安全考虑：JWT 不放进握手 URL query（query 会进入代理/网关访问日志，容易泄露），
  // 而是等连接建立后通过首条 AUTH 消息认证。
  const WS_BASE = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/notifications`

  // 拉取未读数：作为页面挂载/刷新后的兜底同步，与 WS 实时推送互补。
  // 失败静默忽略（未读数非关键数据，后续 WS 推送或手动刷新会纠正）
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
          // 认证成功：标记在线并重置重连计数（之前失败的次数不计入本次连接），随后才启动心跳
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

  // 心跳保活：每 25s 发一次 PING（需小于服务端空闲超时），既能探测断线，也能防止中间设备回收空闲连接。
  // 仅在 AUTH_OK 后才启动，避免对未认证连接发送无意义的心跳
  function startHeartbeat() {
    if (heartbeatTimer) return
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) ws.send('{"type":"PING"}')
    }, 25000)
  }

  // 指数退避重连：间隔 1s → 2s → 4s … 封顶 30s，避免服务端异常时高频重连打满网络/日志；
  // 页面隐藏（document.hidden）或已登出时暂停重连，减少无效请求；重连成功（AUTH_OK）会重置 retry
  function scheduleReconnect() {
    if (document.hidden) return
    const userStore = useUserStore()
    if (!userStore.isLogin()) return
    const delay = Math.min(1000 * 2 ** retry, 30000)
    retry += 1
    reconnectTimer = setTimeout(() => connect(), delay)
  }

  // 主动断开（组件卸载/登出时调用）：先取消待执行的重连，再清理定时器与连接，
  // 并置空 onclose 防止 close 事件再次触发 scheduleReconnect 造成"卸载后仍在后台重连"
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
