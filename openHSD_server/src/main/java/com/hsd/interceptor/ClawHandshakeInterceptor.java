package com.hsd.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * openHSD 插件连接握手拦截器
 * 从 query param 中读取 token，验证身份并将 userId 存入 session attributes
 *
 * 当前为开发阶段：token 格式约定为 "dev_{userId}"，例如 "dev_user001"
 * 后续对接真实用户体系时，替换 validateToken() 方法即可
 */
@Slf4j
@Component
public class ClawHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String query = request.getURI().getQuery();
        String token = extractParam(query, "token");

        if (token == null || token.isBlank()) {
            log.warn("[Handshake] 拒绝连接：未携带 token，来源={}", request.getRemoteAddress());
            return false;
        }

        String userId = validateToken(token);
        if (userId == null) {
            log.warn("[Handshake] 拒绝连接：token 无效，token={}", token);
            return false;
        }

        attributes.put("userId", userId);
        attributes.put("token", token);
        log.info("[Handshake] 握手通过：userId={}，来源={}", userId, request.getRemoteAddress());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
        // 握手后无需额外处理
    }

    /**
     * Token 验证逻辑
     * 开发阶段：token 格式为 "dev_{userId}"，直接解析
     * 生产阶段：替换为查库或 JWT 解析
     */
    private String validateToken(String token) {
        if (token.startsWith("dev_")) {
            String userId = token.substring(4);
            return userId.isBlank() ? null : userId;
        }
        // TODO: 生产环境对接真实 token 验证逻辑
        return null;
    }

    /**
     * 从 query string 中提取指定参数值
     * 例如 "token=abc&other=xyz" -> extractParam(query, "token") -> "abc"
     */
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
