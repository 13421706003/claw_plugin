package com.hsd.config;

import com.hsd.interceptor.ClawHandshakeInterceptor;
import com.hsd.interceptor.WebHandshakeInterceptor;
import com.hsd.websocket.ClawWebSocketHandler;
import com.hsd.websocket.WebWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 路由配置
 *
 * 注册的路径：
 *   /ws/claw        —— openHSD 插件连接入口，需携带 ?token=dev_{userId}
 *   /ws/web/{userId} —— 前端浏览器连接入口，userId 从路径提取
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ClawWebSocketHandler clawWebSocketHandler;
    private final ClawHandshakeInterceptor clawHandshakeInterceptor;
    private final WebWebSocketHandler webWebSocketHandler;
    private final WebHandshakeInterceptor webHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // openHSD 插件连接
        registry.addHandler(clawWebSocketHandler, "/ws/claw")
                .addInterceptors(clawHandshakeInterceptor)
                .setAllowedOrigins("*");

        // 前端浏览器连接（支持 /ws/web/{userId} 和 /ws/web/{userId}/{tabId} 两种格式）
        registry.addHandler(webWebSocketHandler, "/ws/web/**")
                .addInterceptors(webHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
