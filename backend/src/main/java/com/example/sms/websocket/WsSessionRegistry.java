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
 *
 * 为什么用 ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>>：
 * 1) 外层按 userId 分桶，多线程（多连接建立/关闭/推送）并发读写互不阻塞；
 * 2) 内层用 CopyOnWriteArraySet：一个学生可能开多个标签页/多设备，需要一集合挂多个会话；
 *    推送是"读多写少"场景（迭代全部在线会话发消息），CopyOnWriteArraySet 迭代时不加锁、
 *    遍历期间并发增删会话也不会抛 ConcurrentModificationException，写时复制开销可接受。
 */
@Component
public class WsSessionRegistry {

    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void add(Long userId, WebSocketSession session) {
        // computeIfAbsent 原子创建集合：两个连接同时首次建立时不会互相覆盖，也避免先 get 再 put 的竞态
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void remove(Long userId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(userId);
        if (set == null) return;
        set.remove(session);
        // 该用户最后一个会话也断开时，连同外层 key 一起移除，防止 userId 键无限堆积造成内存泄漏
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
                    // 说明：这里不主动 remove，因为 close 回调随后必然触发 remove；此处重复移除反而可能与回调竞争
                }
            } else {
                // 顺手清理已关闭但尚未被回调摘除的会话（兜底，防止下次推送又遍历到无效会话）
                set.remove(session);
            }
        }
    }
}
