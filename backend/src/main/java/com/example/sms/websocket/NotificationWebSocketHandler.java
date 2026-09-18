package com.example.sms.websocket;

import com.example.sms.util.JwtUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 通知 WebSocket 处理器。
 *
 * 安全设计（修复 S-2）：JWT 不在握手 URL query 中传输（避免进入访问日志/代理日志），
 * 改为连接建立后客户端发送首条 AUTH 消息携带 token，服务端校验通过后才注册会话；
 * 未认证连接在 AUTH_TIMEOUT_MS 内未完成认证将被主动关闭（close code 4401）。
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    /** 连接建立后等待 AUTH 的时限（毫秒），超时未认证则关闭 */
    private static final long AUTH_TIMEOUT_MS = 10000;

    /** 未认证被关闭的状态码（前端据此停止重连） */
    public static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized");

    // 定时关闭未认证连接的单线程调度器（守护线程，不阻塞应用退出）
    private static final ScheduledExecutorService AUTH_TIMEOUT =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-auth-timeout");
                t.setDaemon(true);
                return t;
            });

    /** 在线会话注册表：负责会话的增删与消息推送 */
    @Autowired
    private WsSessionRegistry registry;

    /** JWT 工具：解析首条 AUTH 消息中的 token 完成身份认证 */
    @Autowired
    private JwtUtil jwtUtil;

    /** JSON 解析：解析客户端上行的文本帧 */
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 连接建立：不立即注册会话，而是调度一个定时任务——若在 AUTH_TIMEOUT_MS 内未完成认证则主动关闭
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 会话空闲超时（S-3）由 WebSocketConfig 的 ServletServerContainerFactoryBean 统一配置（60s）：
        // 客户端异常断网/心跳中断时由容器及时关闭并触发清理，避免会话残留
        // （前端 25s 心跳保持连接活跃，正常连接不会触发该超时）
        // 不立即注册：等待首条 AUTH 消息认证。未认证连接定时关闭，防止占用会话资源。
        AUTH_TIMEOUT.schedule(() -> {
            try {
                if (session.isOpen() && session.getAttributes().get("userId") == null) {
                    session.close(UNAUTHORIZED);
                }
            } catch (IOException ignored) {
                // 连接已异常，由 onClose/transportError 清理
            }
        }, AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 文本消息处理：未认证阶段仅接受 AUTH 认证帧；已认证阶段仅响应心跳 PING/PONG
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            // 非法帧：未认证连接忽略（等待超时关闭）；已认证连接忽略
            return;
        }
        String type = node.path("type").asText("");

        if (session.getAttributes().get("userId") == null) {
            // ---- 未认证：仅接受 AUTH ----
            // fail-closed：未认证阶段收到任何非 AUTH 帧一律忽略（不响应也不注册），等超时任务关闭连接
            if (!"AUTH".equals(type)) {
                return;
            }
            // asText(null)：token 缺失时返回 null，parseToken 会返回 null，走下面的统一拒绝分支，不单独报错
            String token = node.path("token").asText(null);
            Claims claims = (token != null) ? jwtUtil.parseToken(token) : null;
            // 通知通道仅面向学生端：即使 token 有效，教师/管理员也不接入（他们走轮询刷新），并立即关闭连接
            if (claims == null || !"STUDENT".equals(claims.get("roleType"))) {
                closeUnauthorized(session);
                return;
            }
            // 认证通过：把 userId 写入会话属性并注册进在线表，此后该会话才可收发消息
            Long userId = ((Number) claims.get("userId")).longValue();
            session.getAttributes().put("userId", userId);
            registry.add(userId, session);
            // 回 AUTH_OK 通知前端"注册成功，可开始心跳"
            sendJson(session, "{\"type\":\"AUTH_OK\"}");
            return;
        }

        // ---- 已认证：心跳 ----
        // 只响应 PING/PONG：应用层心跳用于探活，同时客户端借 PONG 确认通道可用；
        // 其它类型的帧在已认证状态同样被忽略（fail-closed，不实现其它业务消息）
        if ("PING".equals(type)) {
            sendJson(session, "{\"type\":\"PONG\"}");
        }
    }

    /**
     * 连接关闭（正常/异常/超时被关）：从在线会话表摘除该会话，避免向失效连接推送
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 无论正常关闭还是被超时/4401 关闭，都从在线表中摘除该会话，防止向已失效连接重复推送
        Object raw = session.getAttributes().get("userId");
        if (raw instanceof Long) registry.remove((Long) raw, session);
    }

    /**
     * 传输层异常（如异常断网）：清理在线表残留会话并尝试按未认证语义关闭
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        // 传输层异常（如客户端异常断网、网络闪断）：主动从在线表清理，避免"半死会话"残留在推送循环里
        Object raw = session.getAttributes().get("userId");
        if (raw instanceof Long) registry.remove((Long) raw, session);
        // 对未认证会话同样走 4401 关闭语义；已认证会话的关闭由 closeUnauthorized 内 isOpen 判断兜底
        closeUnauthorized(session);
    }

    /** 以 4401 状态码关闭会话（若仍处于打开状态） */
    private void closeUnauthorized(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(UNAUTHORIZED);
        } catch (IOException ignored) {
            // no-op
        }
    }

    /** 向会话发送 JSON 文本帧；会话已关闭则静默忽略 */
    private void sendJson(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(json));
        } catch (IOException ignored) {
            // no-op
        }
    }
}
