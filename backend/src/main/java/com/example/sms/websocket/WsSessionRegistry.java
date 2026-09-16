package com.example.sms.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 在线会话注册表（单实例内存实现，支持同一用户多标签页）
 */
@Component
public class WsSessionRegistry {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return;
        set.remove(session);
        if (set.isEmpty()) sessions.remove(userId);
    }

    /** 向指定用户的所有在线会话推送消息；失效会话静默清理 */
    public void sendToUser(Long userId, String json) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return;
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    // 发送失败由 close/error 回调清理，消息已落库不重试
                }
            } else {
                set.remove(session);
            }
        }
    }
}
