package com.hsd.websocket;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClawWebSocketHandler extends TextWebSocketHandler {

    private final ClawSessionRegistry  clawSessionRegistry;
    private final WebSessionRegistry   webSessionRegistry;
    private final MessageService       messageService;
    private final MessageTabRouter     messageTabRouter;
    private final RunIdRegistry        runIdRegistry;

    // ----------------------------------------------------------------
    // 连接生命周期
    // ----------------------------------------------------------------

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = getUserId(session);
        // clawId 在 sync 消息到来后才确定，先用 sessionId 占位
        String tempClawId = "pending_" + session.getId();
        session.getAttributes().put("clawId", tempClawId);

        clawSessionRegistry.register(userId, tempClawId, session);
        log.info("[ClawWS] 连接建立：userId={}，tempClawId={}，在线总数={}",
                userId, tempClawId, clawSessionRegistry.totalOnlineCount());

        sendJson(session, buildJson(
                "type", "connected",
                "userId", userId,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId  = getUserId(session);
        String clawId  = getClawId(session);
        clawSessionRegistry.removeBySession(session);
        log.info("[ClawWS] 连接关闭：userId={}，clawId={}，code={}",
                userId, clawId, status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        String userId = getUserId(session);
        log.error("[ClawWS] 传输异常：userId={}，error={}", userId, exception.getMessage());
        clawSessionRegistry.removeBySession(session);
    }

    // ----------------------------------------------------------------
    // 消息接收与处理
    // ----------------------------------------------------------------

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId  = getUserId(session);
        String payload = message.getPayload();

        JSONObject json;
        try {
            json = JSON.parseObject(payload);
        } catch (Exception e) {
            log.warn("[ClawWS] 非 JSON 消息：userId={}", userId);
            return;
        }

        String type = json.getString("type");
        if (type == null) return;

        switch (type) {
            case "ping"           -> handlePing(session, userId, json);
            case "sync"           -> handleSync(session, userId, json);
            case "run_started"    -> handleRunStarted(session, userId, json);
            case "response"       -> handleResponse(session, userId, json);
            case "response_chunk" -> handleResponseChunk(session, userId, json);
            default               -> log.warn("[ClawWS] 未知消息类型：type={}，userId={}", type, userId);
        }
    }

    // ----------------------------------------------------------------
    // 各类型处理
    // ----------------------------------------------------------------

    private void handlePing(WebSocketSession session, String userId, JSONObject json) {
        String clawId = json.getString("clawId");
        clawSessionRegistry.updateHeartbeat(userId, clawId != null ? clawId : getClawId(session));
        sendJson(session, buildJson("type", "pong", "serverTime", Instant.now().toEpochMilli()));
    }

    /**
     * sync：插件连接后发送，携带 clawId 和 openClawDeviceId
     * 用于：1. 正式注册 clawId  2. 记录 OpenClaw 机器指纹  3. 重推 pending 消息
     */
    private void handleSync(WebSocketSession session, String userId, JSONObject json) {
        String clawId           = json.getString("clawId");
        String openClawDeviceId = json.getString("openClawDeviceId");

        if (clawId != null && !clawId.isBlank()) {
            // 将占位 clawId 替换为真实 clawId
            String tempClawId = getClawId(session);
            if (!clawId.equals(tempClawId)) {
                clawSessionRegistry.removeBySession(session);
                clawSessionRegistry.register(userId, clawId, session);
                session.getAttributes().put("clawId", clawId);
            }
        }

        if (openClawDeviceId != null) {
            clawSessionRegistry.setOpenClawDeviceId(userId, clawId != null ? clawId : getClawId(session), openClawDeviceId);
        }

        log.info("[ClawWS] sync：userId={}，clawId={}，openClawDeviceId={}，在线机器数={}",
                userId, clawId, openClawDeviceId, clawSessionRegistry.getClawCount(userId));

        sendJson(session, buildJson(
                "type",             "sync_ack",
                "userId",           userId,
                "clawId",           clawId,
                "openClawDeviceId", openClawDeviceId != null ? openClawDeviceId : "",
                "pendingCount",     0,
                "serverTime",       Instant.now().toEpochMilli()
        ));
    }

    private void handleRunStarted(WebSocketSession session, String userId, JSONObject json) {
        String messageId = json.getString("messageId");
        String runId     = json.getString("runId");
        String clawId    = getClawId(session);

        if (messageId != null && runId != null) {
            runIdRegistry.register(userId, clawId, messageId, runId);
            log.info("[ClawWS] run_started: userId={}, clawId={}, messageId={}, runId={}",
                    userId, clawId, messageId, runId);
        }
    }

    private void handleResponse(WebSocketSession session, String userId, JSONObject json) {
        String messageId = json.getString("messageId");
        String status    = json.getString("status");
        String result    = json.getString("result");
        String clawId    = getClawId(session);

        log.info("[ClawWS] response：userId={}，clawId={}，messageId={}，status={}", userId, clawId, messageId, status);

        if (result != null && !"pending".equals(status)) {
            try {
                messageService.saveAssistantMessage(messageId, Long.parseLong(userId), clawId, result, status);
            } catch (Exception e) {
                log.error("[ClawWS] 保存助手消息失败：{}", e.getMessage());
            }
        }

        String payload = buildJson(
                "type",      "response",
                "messageId", messageId,
                "status",    status,
                "result",    result
        );

        // 查路由表：精准推送给发起该请求的标签页
        String tabId = messageTabRouter.getTabId(messageId);
        if (tabId != null) {
            webSessionRegistry.pushToTab(userId, tabId, payload);
            // final response 后清理路由条目
            messageTabRouter.remove(messageId);
        } else {
            // 无路由信息（旧格式兼容）：广播给该用户所有在线标签页
            webSessionRegistry.pushToUser(userId, payload);
        }

        if (!"streaming".equals(status)) {
            runIdRegistry.remove(userId, clawId, messageId);
        }
    }

    private void handleResponseChunk(WebSocketSession session, String userId, JSONObject json) {
        String  messageId = json.getString("messageId");
        String  chunk     = json.getString("chunk");
        Integer seq       = json.getInteger("seq");

        log.debug("[ClawWS] chunk：userId={}，messageId={}，seq={}", userId, messageId, seq);

        String payload = buildJson(
                "type",      "response_chunk",
                "messageId", messageId,
                "chunk",     chunk,
                "seq",       seq
        );

        // 查路由表：精准推送给发起该请求的标签页
        String tabId = messageTabRouter.getTabId(messageId);
        if (tabId != null) {
            webSessionRegistry.pushToTab(userId, tabId, payload);
        } else {
            // 无路由信息（旧格式兼容）：广播给该用户所有在线标签页
            webSessionRegistry.pushToUser(userId, payload);
        }
    }

    // ----------------------------------------------------------------
    // 工具方法
    // ----------------------------------------------------------------

    private String getUserId(WebSocketSession session) {
        Object v = session.getAttributes().get("userId");
        return v != null ? v.toString() : "unknown";
    }

    private String getClawId(WebSocketSession session) {
        Object v = session.getAttributes().get("clawId");
        return v != null ? v.toString() : session.getId();
    }

    /**
     * 对外暴露：向指定 session 发消息（供 Controller 调用）
     */
    public boolean sendToSession(WebSocketSession session, String jsonStr) {
        return sendJson(session, jsonStr);
    }

    private boolean sendJson(WebSocketSession session, String jsonStr) {
        if (!session.isOpen()) return false;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(jsonStr));
            }
            return true;
        } catch (IOException e) {
            log.error("[ClawWS] 发送失败：sessionId={}，error={}", session.getId(), e.getMessage());
            return false;
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
