package com.example.sms.config;

import com.example.sms.websocket.NotificationWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * WebSocket 配置：注册通知端点。
 * 认证不再走握手（避免 JWT 出现在 URL query 暴露于日志），
 * 改由 NotificationWebSocketHandler 在连接后首条 AUTH 消息完成（S-2 修复）。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private NotificationWebSocketHandler notificationWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                // 允许任意来源跨域连接：前端与后端可能分属不同端口/域名（开发环境常见）；
                // WebSocket 握手不携带 Cookie 等凭据（认证改走 AUTH 消息），故通配 origin 不会引入 CSRF 类风险
                .setAllowedOriginPatterns("*");
    }

    /**
     * S-3：显式配置 WebSocket 会话空闲超时（60s）。
     * 客户端异常断网/心跳中断时由容器及时关闭会话并触发清理，避免会话残留；
     * 前端 25s 心跳保持连接活跃，正常连接不会触发该超时。
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxSessionIdleTimeout(60000L);
        return container;
    }
}
