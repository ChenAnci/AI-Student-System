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

    private static final ScheduledExecutorService AUTH_TIMEOUT =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "ws-auth-timeout");
                t.setDaemon(true);
                return t;
            });

    @Autowired
    private WsSessionRegistry registry;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
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
            if (!"AUTH".equals(type)) {
                return;
            }
            String token = node.path("token").asText(null);
            Claims claims = (token != null) ? jwtUtil.parseToken(token) : null;
            if (claims == null || !"STUDENT".equals(claims.get("roleType"))) {
                closeUnauthorized(session);
                return;
            }
            Long userId = ((Number) claims.get("userId")).longValue();
            session.getAttributes().put("userId", userId);
            registry.add(userId, session);
            sendJson(session, "{\"type\":\"AUTH_OK\"}");
            return;
        }

        // ---- 已认证：心跳 ----
        if ("PING".equals(type)) {
            sendJson(session, "{\"type\":\"PONG\"}");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object raw = session.getAttributes().get("userId");
        if (raw instanceof Long) registry.remove((Long) raw, session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Object raw = session.getAttributes().get("userId");
        if (raw instanceof Long) registry.remove((Long) raw, session);
        closeUnauthorized(session);
    }

    private void closeUnauthorized(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(UNAUTHORIZED);
        } catch (IOException ignored) {
            // no-op
        }
    }

    private void sendJson(WebSocketSession session, String json) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(json));
        } catch (IOException ignored) {
            // no-op
        }
    }
}
