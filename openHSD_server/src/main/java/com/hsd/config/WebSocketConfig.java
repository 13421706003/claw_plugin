package com.hsd.config;

import com.hsd.interceptor.ClawHandshakeInterceptor;
import com.hsd.websocket.ClawWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 路由配置
 *
 * 当前注册的路径：
 *   /ws/claw  —— openHSD 插件连接入口，需携带 ?token=dev_{userId}
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClawWebSocketHandler clawWebSocketHandler;
    private final ClawHandshakeInterceptor clawHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(clawWebSocketHandler, "/ws/claw")
                .addInterceptors(clawHandshakeInterceptor)
                .setAllowedOrigins("*");  // 开发阶段放开跨域，生产环境替换为具体域名
    }
}
