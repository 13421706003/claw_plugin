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
 * 支持两种 URL 格式：
 *   /ws/web/{userId}          —— 兼容旧格式（手机端、第三方客户端）
 *   /ws/web/{userId}/{tabId}  —— 多标签页精准路由
 */
@Slf4j
@Component
public class WebHandshakeInterceptor implements HandshakeInterceptor {

    /** 匹配 /ws/web/{userId}/{tabId} */
    private static final Pattern WITH_TAB_PATTERN = Pattern.compile("/ws/web/([^/]+)/([^/]+)$");
    /** 匹配 /ws/web/{userId} */
    private static final Pattern NO_TAB_PATTERN   = Pattern.compile("/ws/web/([^/]+)$");

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        
        String path = request.getURI().getPath();

        // 优先匹配带 tabId 的格式
        Matcher withTab = WITH_TAB_PATTERN.matcher(path);
        if (withTab.find()) {
            String userId = withTab.group(1);
            String tabId  = withTab.group(2);
            attributes.put("userId", userId);
            attributes.put("tabId",  tabId);
            log.info("[WebHandshake] 握手通过（含tabId）：userId={}，tabId={}，path={}", userId, tabId, path);
            return true;
        }

        // 兼容旧格式（无 tabId），用 WebSocket sessionId 在注册时兜底
        Matcher noTab = NO_TAB_PATTERN.matcher(path);
        if (noTab.find()) {
            String userId = noTab.group(1);
            attributes.put("userId", userId);
            // tabId 留空，由 WebWebSocketHandler 用 session.getId() 填充
            log.info("[WebHandshake] 握手通过（无tabId）：userId={}，path={}", userId, path);
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
