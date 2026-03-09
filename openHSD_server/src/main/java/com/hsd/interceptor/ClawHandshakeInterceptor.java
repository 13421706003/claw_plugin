package com.hsd.interceptor;

import com.hsd.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * openHSD 插件连接握手拦截器
 * 从 query param 中读取 JWT token，验证身份并将 userId 存入 session attributes
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClawHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String query = request.getURI().getQuery();
        String token = extractParam(query, "token");

        if (token == null || token.isBlank()) {
            log.warn("[ClawHandshake] 拒绝连接：未携带 token，来源={}", request.getRemoteAddress());
            return false;
        }

        Long userId = jwtUtil.getUserId(token);
        String username = jwtUtil.getUsername(token);

        if (userId == null) {
            log.warn("[ClawHandshake] 拒绝连接：JWT 无效，来源={}", request.getRemoteAddress());
            return false;
        }

        attributes.put("userId", String.valueOf(userId));
        attributes.put("username", username);
        log.info("[ClawHandshake] 握手通过：userId={}，username={}，来源={}", userId, username, request.getRemoteAddress());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String extractParam(String query, String paramName) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(paramName)) {
                return kv[1];
            }
        }
        return null;
    }
}
