package com.example.sms.config; // 声明当前类所属包：本项目所有配置类统一放在 com.example.sms.config 包下

// ---- import 区域：引入 WebSocket 配置所需的 Spring 与业务处理器类 ----
import com.example.sms.websocket.NotificationWebSocketHandler; // 通知 WebSocket 处理器：负责连接后的认证与消息推送
import org.springframework.beans.factory.annotation.Autowired; // @Autowired 注解：按类型自动注入依赖
import org.springframework.context.annotation.Bean; // @Bean 注解：将方法返回值注册为 Spring 容器管理的 Bean
import org.springframework.context.annotation.Configuration; // @Configuration 注解：标记该类为 Spring 配置类
import org.springframework.web.socket.config.annotation.EnableWebSocket; // 启用 Spring WebSocket 支持
import org.springframework.web.socket.config.annotation.WebSocketConfigurer; // WebSocket 配置接口：用于注册处理器
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry; // WebSocket 处理器注册表
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean; // WebSocket 容器工厂：可配置会话参数

/**
 * WebSocket 配置：注册通知端点。
 * 认证不再走握手（避免 JWT 出现在 URL query 暴露于日志），
 * 改由 NotificationWebSocketHandler 在连接后首条 AUTH 消息完成（S-2 修复）。
 */
@Configuration // 标记为 Spring 配置类：启动时自动加载生效
@EnableWebSocket // 启用 Spring 的 WebSocket 支持
public class WebSocketConfig implements WebSocketConfigurer { // 实现 WebSocketConfigurer：注册 WebSocket 处理器到指定端点

    // 通知 WebSocket 处理器：负责连接建立后的 AUTH 认证与消息推送
    @Autowired // 注入通知 WebSocket 处理器
    private NotificationWebSocketHandler notificationWebSocketHandler; // 业务处理器实例（处理连接、认证、推送）

    @Override // 重写 registerWebSocketHandlers 方法
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) { // 注册 WebSocket 端点
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications") // 把通知处理器绑定到 /ws/notifications 端点
                // 允许任意来源跨域连接：前端与后端可能分属不同端口/域名（开发环境常见）；
                // WebSocket 握手不携带 Cookie 等凭据（认证改走 AUTH 消息），故通配 origin 不会引入 CSRF 类风险
                .setAllowedOriginPatterns("*"); // 允许所有来源跨域连接（身份认证在业务层通过 AUTH 消息完成）
    }

    /**
     * S-3：显式配置 WebSocket 会话空闲超时（60s）。
     * 客户端异常断网/心跳中断时由容器及时关闭会话并触发清理，避免会话残留；
     * 前端 25s 心跳保持连接活跃，正常连接不会触发该超时。
     */
    @Bean // 注册容器工厂 Bean：覆盖容器的默认 WebSocket 配置
    public ServletServerContainerFactoryBean createWebSocketContainer() { // 构建并返回 WebSocket 容器工厂
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean(); // 创建容器工厂实例（用于配置会话级参数）
        container.setMaxSessionIdleTimeout(60000L); // 设置会话最大空闲超时 60 秒：异常断线/心跳中断时容器及时回收会话
        return container; // 返回配置好的容器工厂
    }
}
