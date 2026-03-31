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
 * 支持路径：
 *   /ws/web/{userId}          —— 兼容旧格式（手机端、第三方客户端）
 *   /ws/web/{userId}/{tabId}  —— 多标签页精准路由
 *
 * 职责：
 * 1. 管理前端连接的注册/注销（以 tabId 区分同一用户的不同标签页）
 * 2. 接收 ping 心跳并回复 pong
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebWebSocketHandler extends TextWebSocketHandler {

    private final WebSessionRegistry webSessionRegistry;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        // 优先使用握手时从 URL 中提取的 tabId，否则用 sessionId 兜底（旧格式兼容）
        String tabId  = getOrInitTabId(session);

        webSessionRegistry.register(userId, tabId, session);
        log.info("[WebWS] 前端连接建立：userId={}，tabId={}，sessionId={}", userId, tabId, session.getId());

        sendJson(session, buildJson(
                "type",       "connected",
                "userId",     userId,
                "tabId",      tabId,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserId(session);
        String tabId  = getTabId(session);
        webSessionRegistry.remove(userId, tabId);
        log.info("[WebWS] 前端连接关闭：userId={}，tabId={}，code={}", userId, tabId, status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = getUserId(session);
        String tabId  = getTabId(session);
        log.error("[WebWS] 传输异常：userId={}，tabId={}，error={}", userId, tabId, exception.getMessage());
        webSessionRegistry.remove(userId, tabId);
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

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    private String getUserId(WebSocketSession session) {
        Object v = session.getAttributes().get("userId");
        return v != null ? v.toString() : "unknown";
    }

    /**
     * 获取已存入 attributes 的 tabId（连接建立后可用）
     */
    private String getTabId(WebSocketSession session) {
        Object v = session.getAttributes().get("tabId");
        return v != null ? v.toString() : session.getId();
    }

    /**
     * 连接建立时：若握手已注入 tabId 则直接取用；
     * 否则用 sessionId 作为 tabId 并写回 attributes，保证后续 remove 能对上。
     */
    private String getOrInitTabId(WebSocketSession session) {
        Object v = session.getAttributes().get("tabId");
        if (v != null && !v.toString().isBlank()) {
            return v.toString();
        }
        String fallback = session.getId();
        session.getAttributes().put("tabId", fallback);
        return fallback;
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
