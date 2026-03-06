package com.hsd.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;

/**
 * 前端浏览器的 WebSocket Handler
 * 路径：/ws/web/{userId}
 * 
 * 职责：
 * 1. 管理前端连接的注册/注销
 * 2. 接收来自 ClawWebSocketHandler 转发的消息并推送给前端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebWebSocketHandler extends TextWebSocketHandler {

    private final WebSessionRegistry webSessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        webSessionRegistry.register(userId, session);
        log.info("[WebWS] 前端连接建立：userId={}，sessionId={}", userId, session.getId());
        
        sendJson(session, buildJson(
                "type", "connected",
                "userId", userId,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserId(session);
        webSessionRegistry.remove(userId);
        log.info("[WebWS] 前端连接关闭：userId={}，code={}", userId, status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = getUserId(session);
        log.error("[WebWS] 传输异常：userId={}，error={}", userId, exception.getMessage());
        webSessionRegistry.remove(userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = getUserId(session);
        String payload = message.getPayload();
        
        JSONObject json;
        try {
            json = JSON.parseObject(payload);
        } catch (Exception e) {
            log.warn("[WebWS] 收到非 JSON 消息：userId={}", userId);
            return;
        }

        String type = json.getString("type");
        if ("ping".equals(type)) {
            sendJson(session, buildJson("type", "pong", "serverTime", Instant.now().toEpochMilli()));
        } else {
            log.debug("[WebWS] 收到消息：userId={}，type={}", userId, type);
        }
    }

    private String getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId != null ? userId.toString() : "unknown";
    }

    private void sendJson(WebSocketSession session, String jsonStr) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new TextMessage(jsonStr));
        } catch (IOException e) {
            log.error("[WebWS] 发送消息失败：error={}", e.getMessage());
        }
    }

    private String buildJson(Object... kvPairs) {
        JSONObject obj = new JSONObject();
        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            obj.put(kvPairs[i].toString(), kvPairs[i + 1]);
        }
        return obj.toJSONString();
    }
}
