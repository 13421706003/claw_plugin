package com.hsd.controller;

import com.alibaba.fastjson2.JSONObject;
import com.hsd.websocket.ClawWebSocketHandler;
import com.hsd.websocket.SessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

/**
 * 向 openHSD 插件发送消息的 REST 接口
 * 用于测试后端 → 插件方向的通信
 */
@RestController
@RequestMapping("/api/claw")
@RequiredArgsConstructor
public class ClawController {

    private final SessionRegistry sessionRegistry;
    private final ClawWebSocketHandler clawWebSocketHandler;

    /**
     * 查询指定用户的插件在线状态
     * GET /api/claw/status?userId=user001
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestParam String userId) {
        boolean online = sessionRegistry.isOnline(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("online", online);
        result.put("onlineCount", sessionRegistry.onlineCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 向指定用户的插件发送消息，插件会转发给 OpenClaw 执行
     *
     * POST /api/claw/send
     * Body:
     * {
     *   "userId":    "user001",       // 目标用户
     *   "messageId": "msg_001",       // 消息唯一ID（调用方生成）
     *   "content":   "帮我查一下天气", // 发给 OpenClaw 的内容
     *   "context": {                  // 可选
     *     "sessionKey": "main",
     *     "thinking": "medium"
     *   },
     *   "timeout": 30000              // 可选，超时毫秒数
     * }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        String userId    = (String) body.get("userId");
        String messageId = (String) body.get("messageId");
        String content   = (String) body.get("content");

        Map<String, Object> result = new HashMap<>();

        if (userId == null || messageId == null || content == null) {
            result.put("success", false);
            result.put("message", "缺少必填字段：userId / messageId / content");
            return ResponseEntity.badRequest().body(result);
        }

        WebSocketSession session = sessionRegistry.get(userId);
        if (session == null || !session.isOpen()) {
            result.put("success", false);
            result.put("online", false);
            result.put("message", "用户 " + userId + " 的插件不在线");
            return ResponseEntity.ok(result);
        }

        // 构造下发给插件的标准 request 消息
        JSONObject payload = new JSONObject();
        payload.put("type",      "request");
        payload.put("messageId", messageId);
        payload.put("content",   content);
        if (body.containsKey("context")) payload.put("context", body.get("context"));
        if (body.containsKey("timeout")) payload.put("timeout", body.get("timeout"));

        boolean sent = clawWebSocketHandler.sendToSession(session, payload.toJSONString());

        result.put("success",   sent);
        result.put("userId",    userId);
        result.put("messageId", messageId);
        result.put("message",   sent ? "已下发给插件，等待 OpenClaw 回复" : "发送失败");
        return ResponseEntity.ok(result);
    }
}
