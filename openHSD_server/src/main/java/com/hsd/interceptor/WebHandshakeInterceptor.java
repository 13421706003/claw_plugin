package com.hsd.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 前端 WebSocket 握手拦截器
 * 从 URL 路径 /ws/web/{userId} 中提取 userId
 */
@Slf4j
@Component
public class WebHandshakeInterceptor implements HandshakeInterceptor {

    private static final Pattern USER_ID_PATTERN = Pattern.compile("/ws/web/([^/]+)$");

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        
        String path = request.getURI().getPath();
        Matcher matcher = USER_ID_PATTERN.matcher(path);
        
        if (matcher.find()) {
            String userId = matcher.group(1);
            attributes.put("userId", userId);
            log.info("[WebHandshake] 握手通过：userId={}，path={}", userId, path);
            return true;
        }
        
        log.warn("[WebHandshake] 拒绝连接：无法从路径提取 userId，path={}", path);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
