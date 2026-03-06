package com.hsd.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;

/**
 * openHSD 插件的 WebSocket 核心 Handler
 * 路径：/ws/claw
 *
 * 职责：
 * 1. 连接建立 → 注册到 SessionRegistry
 * 2. 收到消息 → 根据 type 分发处理（ping / sync / response / response_chunk）
 * 3. 连接关闭 → 从 SessionRegistry 移除
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClawWebSocketHandler extends TextWebSocketHandler {

    private final SessionRegistry sessionRegistry;
    private final WebSessionRegistry webSessionRegistry;

    // ----------------------------------------------------------------
    // 连接生命周期
    // ----------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        sessionRegistry.register(userId, session);
        log.info("[ClawWS] 连接建立：userId={}，sessionId={}，当前在线数={}",
                userId, session.getId(), sessionRegistry.onlineCount());

        // 连接成功后，主动告知插件连接已就绪
        sendJson(session, buildJson("type", "connected",
                "userId", userId,
                "serverTime", Instant.now().toEpochMilli()));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = getUserId(session);
        sessionRegistry.remove(userId);
        log.info("[ClawWS] 连接关闭：userId={}，code={}，reason={}",
                userId, status.getCode(), status.getReason());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = getUserId(session);
        log.error("[ClawWS] 传输异常：userId={}，error={}", userId, exception.getMessage());
        sessionRegistry.remove(userId);
    }

    // ----------------------------------------------------------------
    // 消息接收与处理
    // ----------------------------------------------------------------

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = getUserId(session);
        String payload = message.getPayload();

        JSONObject json;
        try {
            json = JSON.parseObject(payload);
        } catch (Exception e) {
            log.warn("[ClawWS] 收到非 JSON 消息：userId={}，payload={}", userId, payload);
            return;
        }

        String type = json.getString("type");
        if (type == null) {
            log.warn("[ClawWS] 消息缺少 type 字段：userId={}", userId);
            return;
        }

        switch (type) {
            case "ping"           -> handlePing(session, userId, json);
            case "sync"           -> handleSync(session, userId, json);
            case "response"       -> handleResponse(session, userId, json);
            case "response_chunk" -> handleResponseChunk(session, userId, json);
            default               -> log.warn("[ClawWS] 未知消息类型：type={}，userId={}", type, userId);
        }
    }

    // ----------------------------------------------------------------
    // 各类型消息处理
    // ----------------------------------------------------------------

    /**
     * 心跳 ping：回复 pong，记录最后心跳时间
     */
    private void handlePing(WebSocketSession session, String userId, JSONObject json) {
        String clawId = json.getString("clawId");
        log.debug("[ClawWS] ping 收到：userId={}，clawId={}", userId, clawId);

        sendJson(session, buildJson(
                "type", "pong",
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    /**
     * 重连同步：openHSD 重连后发送，云端批量重推 pending 消息
     * TODO: 后续对接 MessageService，查询并重推 pending 消息
     */
    private void handleSync(WebSocketSession session, String userId, JSONObject json) {
        String clawId = json.getString("clawId");
        log.info("[ClawWS] sync 收到：userId={}，clawId={}，准备重推 pending 消息", userId, clawId);

        // TODO: messageService.getPendingMessages(userId) -> 逐条推送
        // 当前阶段：回复 sync_ack 确认收到
        sendJson(session, buildJson(
                "type", "sync_ack",
                "userId", userId,
                "pendingCount", 0,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    /**
     * 执行结果（终态）：completed / error
     * 插件在 OpenClaw 返回 final / error / aborted 后发送此消息
     */
    private void handleResponse(WebSocketSession session, String userId, JSONObject json) {
        String messageId = json.getString("messageId");
        String status    = json.getString("status");
        String result    = json.getString("result");

        log.info("[ClawWS] response 收到：userId={}，messageId={}，status={}", userId, messageId, status);

        // 转发给前端
        String jsonStr = buildJson(
                "type", "response",
                "messageId", messageId,
                "status", status,
                "result", result
        );
        webSessionRegistry.pushToUser(userId, jsonStr);
    }

    /**
     * 流式响应 chunk（OpenClaw delta 事件转发过来的）
     */
    private void handleResponseChunk(WebSocketSession session, String userId, JSONObject json) {
        String messageId = json.getString("messageId");
        String chunk     = json.getString("chunk");
        Integer seq      = json.getInteger("seq");

        log.debug("[ClawWS] response_chunk 收到：userId={}，messageId={}，seq={}", userId, messageId, seq);

        // 转发给前端
        String jsonStr = buildJson(
                "type", "response_chunk",
                "messageId", messageId,
                "chunk", chunk,
                "seq", seq
        );
        webSessionRegistry.pushToUser(userId, jsonStr);
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    private String getUserId(WebSocketSession session) {
        Object userId = session.getAttributes().get("userId");
        return userId != null ? userId.toString() : "unknown";
    }

    /**
     * 对外暴露：供 Controller 等外部组件向指定 session 发送消息
     */
    public boolean sendToSession(WebSocketSession session, String jsonStr) {
        return sendJson(session, jsonStr);
    }

    /**
     * 发送 JSON 消息，返回是否成功
     */
    private boolean sendJson(WebSocketSession session, String jsonStr) {
        if (!session.isOpen()) return false;
        try {
            session.sendMessage(new TextMessage(jsonStr));
            return true;
        } catch (IOException e) {
            log.error("[ClawWS] 发送消息失败：sessionId={}，error={}", session.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 构建 JSON 字符串，参数为 key-value 交替传入
     * buildJson("type", "pong", "serverTime", 123456L) -> {"type":"pong","serverTime":123456}
     */
    private String buildJson(Object... kvPairs) {
        JSONObject obj = new JSONObject();
        for (int i = 0; i < kvPairs.length - 1; i += 2) {
            obj.put(kvPairs[i].toString(), kvPairs[i + 1]);
        }
        return obj.toJSONString();
    }
}
