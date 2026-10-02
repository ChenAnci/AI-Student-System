package com.example.sms.websocket; // 声明该类所在包：websocket 包，存放实时通知相关组件

// ---- import 区域：引入 Spring 组件注解、WebSocket 消息/会话类，以及并发安全的集合类 ----
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
@Component // Spring 组件注解：把注册表注册为 Bean，供 WebSocket 处理器注入使用
public class WsSessionRegistry { // 在线会话注册表：维护"用户 → 其全部在线 WebSocket 会话"的映射

    /** 在线会话表：userId → 该用户的全部在线会话集合 */
    private final ConcurrentHashMap<Long, CopyOnWriteArraySet<WebSocketSession>> sessions = new ConcurrentHashMap<>(); // 外层按用户分桶、内层用写时复制集合，支持并发读写与多标签页

    /** 注册一个在线会话（同一用户多标签页会各自独立注册） */
    public void add(Long userId, WebSocketSession session) { // 注册方法：把一个会话挂到对应用户名下
        // computeIfAbsent 原子创建集合：两个连接同时首次建立时不会互相覆盖，也避免先 get 再 put 的竞态
        sessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(session); // 键不存在则原子创建集合再添加会话（线程安全）
    }

    /** 移除某个会话；该用户最后一个会话断开时一并清理外层 key */
    public void remove(Long userId, WebSocketSession session) { // 移除方法：连接断开时从在线表摘除
        Set<WebSocketSession> set = sessions.get(userId); // 取出该用户的会话集合
        if (set == null) return; // 用户不在线（集合不存在）则无需处理
        set.remove(session); // 从集合中移除该会话
        // 该用户最后一个会话也断开时，连同外层 key 一起移除，防止 userId 键无限堆积造成内存泄漏
        if (set.isEmpty()) sessions.remove(userId); // 集合已空则一并删除外层键，避免内存泄漏
    }

    /** 向指定用户的所有在线会话推送消息；失效会话静默清理 */
    public void sendToUser(Long userId, String json) { // 推送方法：业务端调用，向该用户全部在线会话发消息
        Set<WebSocketSession> set = sessions.get(userId); // 取出该用户的全部在线会话
        if (set == null) return; // 用户不在线（无会话）则直接返回，无需推送
        for (WebSocketSession session : set) { // 遍历该用户的每个在线会话（多标签页逐一推送）
            if (session.isOpen()) { // 会话仍处于打开状态才推送
                try { // try 块：发送可能抛 IO 异常
                    session.sendMessage(new TextMessage(json)); // 把 JSON 文本帧发送给该会话
                } catch (IOException e) { // 发送失败（连接已断等）
                    // 发送失败由 close/error 回调清理，消息已落库不重试
                    // 说明：这里不主动 remove，因为 close 回调随后必然触发 remove；此处重复移除反而可能与回调竞争
                }
            } else { // 会话已关闭但尚未被回调摘除
                // 顺手清理已关闭但尚未被回调摘除的会话（兜底，防止下次推送又遍历到无效会话）
                set.remove(session); // 直接从集合中移除失效会话（CopyOnWriteArraySet 迭代时安全移除）
            }
        }
    }
}
