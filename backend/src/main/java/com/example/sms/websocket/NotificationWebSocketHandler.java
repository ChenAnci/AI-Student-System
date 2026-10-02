package com.example.sms.websocket; // 声明该类所在包：websocket 包，存放实时通知相关组件

// ---- import 区域：引入 JWT 解析、Jackson JSON 解析、Spring WebSocket 会话/消息类、并发调度器等 ----
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
@Component // Spring 组件注解：把该处理器注册为 Bean，由 WebSocket 配置绑定到指定路径
public class NotificationWebSocketHandler extends TextWebSocketHandler { // 继承文本 WebSocket 处理器：接管连接建立/文本消息/关闭/异常等回调

    /** 连接建立后等待 AUTH 的时限（毫秒），超时未认证则关闭 */
    private static final long AUTH_TIMEOUT_MS = 10000; // 认证超时时间：连接建立后 10 秒内未完成认证即判定为未授权连接

    /** 未认证被关闭的状态码（前端据此停止重连） */
    public static final CloseStatus UNAUTHORIZED = new CloseStatus(4401, "unauthorized"); // 自定义关闭码 4401：前端收到后停止自动重连，避免死循环

    // 定时关闭未认证连接的单线程调度器（守护线程，不阻塞应用退出）
    private static final ScheduledExecutorService AUTH_TIMEOUT = // 全局共享的单线程调度器：用来延时执行"超时关闭未认证连接"任务
            Executors.newSingleThreadScheduledExecutor(r -> { // 创建单线程定时调度线程池，并自定义线程工厂
                Thread t = new Thread(r, "ws-auth-timeout"); // 创建带名字的线程（便于日志与排查）
                t.setDaemon(true); // 设为守护线程：应用退出时不阻塞 JVM 关闭
                return t; // 返回自定义线程给调度器使用
            }); // 结束线程工厂 lambda 与调度器创建表达式

    /** 在线会话注册表：负责会话的增删与消息推送 */
    @Autowired // 依赖注入：自动注入 WsSessionRegistry（在线会话管理）
    private WsSessionRegistry registry; // 在线会话注册表引用，用于会话注册/摘除/推送

    /** JWT 工具：解析首条 AUTH 消息中的 token 完成身份认证 */
    @Autowired // 依赖注入：自动注入 JwtUtil（令牌解析）
    private JwtUtil jwtUtil; // JWT 工具引用，用于认证阶段解析客户端携带的 token

    /** JSON 解析：解析客户端上行的文本帧 */
    @Autowired // 依赖注入：自动注入 ObjectMapper（JSON 解析）
    private ObjectMapper objectMapper; // JSON 解析器引用，用于解析客户端上行的文本帧

    /**
     * 连接建立：不立即注册会话，而是调度一个定时任务——若在 AUTH_TIMEOUT_MS 内未完成认证则主动关闭
     */
    @Override // 重写父类回调：连接建立成功后触发
    public void afterConnectionEstablished(WebSocketSession session) { // 连接建立回调：只安排认证超时关闭，不立即注册
        // 会话空闲超时（S-3）由 WebSocketConfig 的 ServletServerContainerFactoryBean 统一配置（60s）：
        // 客户端异常断网/心跳中断时由容器及时关闭并触发清理，避免会话残留
        // （前端 25s 心跳保持连接活跃，正常连接不会触发该超时）
        // 不立即注册：等待首条 AUTH 消息认证。未认证连接定时关闭，防止占用会话资源。
        AUTH_TIMEOUT.schedule(() -> { // 调度一个延时任务：在认证超时时刻执行关闭判断
            try { // try 块：捕获关闭会话时的 IO 异常
                if (session.isOpen() && session.getAttributes().get("userId") == null) { // 连接仍打开且尚未认证（没有 userId 属性）才关闭
                    session.close(UNAUTHORIZED); // 以 4401 状态码主动关闭未认证连接，释放资源
                }
            } catch (IOException ignored) { // 关闭失败（连接可能已断）忽略
                // 连接已异常，由 onClose/transportError 清理
            }
        }, AUTH_TIMEOUT_MS, TimeUnit.MILLISECONDS); // 指定延时 10 秒后执行上述关闭任务
    }

    /**
     * 文本消息处理：未认证阶段仅接受 AUTH 认证帧；已认证阶段仅响应心跳 PING/PONG
     */
    @Override // 重写父类回调：收到客户端文本消息时触发
    protected void handleTextMessage(WebSocketSession session, TextMessage message) { // 文本消息处理：按认证状态分流处理
        JsonNode node; // 声明 JSON 节点变量，用于存放解析结果
        try { // try 块：解析客户端上行的 JSON 文本
            node = objectMapper.readTree(message.getPayload()); // 把消息体解析成 JSON 树
        } catch (Exception e) { // 解析失败（非法 JSON / 空消息等）
            // 非法帧：未认证连接忽略（等待超时关闭）；已认证连接忽略
            return; // 直接忽略非法帧，不响应也不注册（fail-closed）
        }
        String type = node.path("type").asText(""); // 取消息中的 type 字段，缺省为空串，用于后续分支判断

        if (session.getAttributes().get("userId") == null) { // 会话属性中无 userId：当前处于"未认证"阶段
            // ---- 未认证：仅接受 AUTH ----
            // fail-closed：未认证阶段收到任何非 AUTH 帧一律忽略（不响应也不注册），等超时任务关闭连接
            if (!"AUTH".equals(type)) { // 首帧不是 AUTH 认证消息
                return; // 忽略该帧（fail-closed），不响应、不注册，等待超时关闭
            }
            // asText(null)：token 缺失时返回 null，parseToken 会返回 null，走下面的统一拒绝分支，不单独报错
            String token = node.path("token").asText(null); // 从 AUTH 帧取 token 字段，缺失时为 null
            Claims claims = (token != null) ? jwtUtil.parseToken(token) : null; // 有 token 才解析验签，否则直接视为无效
            // 通知通道仅面向学生端：即使 token 有效，教师/管理员也不接入（他们走轮询刷新），并立即关闭连接
            if (claims == null || !"STUDENT".equals(claims.get("roleType"))) { // 认证失败，或角色不是学生
                closeUnauthorized(session); // 立即以 4401 关闭该连接
                return; // 结束处理，不注册会话
            }
            // 认证通过：把 userId 写入会话属性并注册进在线表，此后该会话才可收发消息
            Long userId = ((Number) claims.get("userId")).longValue(); // 从 token 声明中取出用户 ID 并转成 Long
            session.getAttributes().put("userId", userId); // 把 userId 写入会话属性：标记"已认证"，后续回调据此判断
            registry.add(userId, session); // 把该会话注册进在线表，之后可向其推送通知
            // 回 AUTH_OK 通知前端"注册成功，可开始心跳"
            sendJson(session, "{\"type\":\"AUTH_OK\"}"); // 给客户端回 AUTH_OK 确认帧，通知其可开始心跳
            return; // 处理完成返回
        }

        // ---- 已认证：心跳 ----
        // 只响应 PING/PONG：应用层心跳用于探活，同时客户端借 PONG 确认通道可用；
        // 其它类型的帧在已认证状态同样被忽略（fail-closed，不实现其它业务消息）
        if ("PING".equals(type)) { // 已认证阶段只处理心跳 PING
            sendJson(session, "{\"type\":\"PONG\"}"); // 回复 PONG，确认连接与通道仍可用
        }
    }

    /**
     * 连接关闭（正常/异常/超时被关）：从在线会话表摘除该会话，避免向失效连接推送
     */
    @Override // 重写父类回调：连接关闭（任意原因）时触发
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) { // 连接关闭回调：清理在线表中的会话
        // 无论正常关闭还是被超时/4401 关闭，都从在线表中摘除该会话，防止向已失效连接重复推送
        Object raw = session.getAttributes().get("userId"); // 取出该会话的 userId 属性（可能是 Long 或 null）
        if (raw instanceof Long) registry.remove((Long) raw, session); // 已认证过（有 userId）才需要从在线表中移除
    }

    /**
     * 传输层异常（如异常断网）：清理在线表残留会话并尝试按未认证语义关闭
     */
    @Override // 重写父类回调：底层传输出现异常时触发
    public void handleTransportError(WebSocketSession session, Throwable exception) { // 传输异常回调：清理可能残留的"半死会话"
        // 传输层异常（如客户端异常断网、网络闪断）：主动从在线表清理，避免"半死会话"残留在推送循环里
        Object raw = session.getAttributes().get("userId"); // 取出会话的 userId 属性
        if (raw instanceof Long) registry.remove((Long) raw, session); // 已认证会话从在线表摘除
        // 对未认证会话同样走 4401 关闭语义；已认证会话的关闭由 closeUnauthorized 内 isOpen 判断兜底
        closeUnauthorized(session); // 尝试按 4401 语义关闭（若连接仍打开则关闭，否则静默）
    }

    /** 以 4401 状态码关闭会话（若仍处于打开状态） */
    private void closeUnauthorized(WebSocketSession session) { // 私有方法：统一以 4401 关闭未授权连接
        try { // try 块：关闭可能抛 IO 异常
            if (session.isOpen()) session.close(UNAUTHORIZED); // 仅当连接仍打开时才关闭，避免重复关闭报错
        } catch (IOException ignored) { // 关闭失败（连接已断等）忽略
            // no-op
        }
    }

    /** 向会话发送 JSON 文本帧；会话已关闭则静默忽略 */
    private void sendJson(WebSocketSession session, String json) { // 私有方法：向指定会话发送 JSON 文本帧
        try { // try 块：发送可能抛 IO 异常
            if (session.isOpen()) session.sendMessage(new TextMessage(json)); // 仅当连接打开时才发送，避免向失效连接写入报错
        } catch (IOException ignored) { // 发送失败（连接已断）忽略，交由 close/error 回调清理
            // no-op
        }
    }
}
