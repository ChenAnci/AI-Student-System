package com.example.sms.websocket;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

/**
 * 通知 WebSocket 处理器：连接注册/断开清理；心跳 PING/PONG
 */
@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {

    @Autowired
    private WsSessionRegistry registry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Object raw = session.getAttributes().get("userId");
        if (!(raw instanceof Long)) {
            closeQuietly(session);
            return;
        }
        registry.add((Long) raw, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if ("{\"type\":\"PING\"}".equals(message.getPayload())) {
            session.sendMessage(new TextMessage("{\"type\":\"PONG\"}"));
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
        closeQuietly(session);
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException ignored) {
            // no-op
        }
    }
}
