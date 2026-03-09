package com.hsd.controller;

import com.alibaba.fastjson2.JSONObject;
import com.hsd.service.MessageService;
import com.hsd.websocket.ClawWebSocketHandler;
import com.hsd.websocket.ClawSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * openHSD 插件消息 REST 接口
 */
@RestController
@RequestMapping("/api/claw")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ClawController {

    private final ClawSessionRegistry   clawSessionRegistry;
    private final ClawWebSocketHandler  clawWebSocketHandler;
    private final MessageService        messageService;

    /**
     * 查询指定用户的插件在线状态及所有连接机器信息
     * GET /api/claw/status?userId=xxx
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestParam String userId) {
        boolean online = clawSessionRegistry.isOnline(userId);
        List<Map<String, Object>> clawList = clawSessionRegistry.getOnlineClawInfo(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("userId",       userId);
        result.put("online",       online);
        result.put("clawCount",    clawList.size());
        result.put("clawList",     clawList);           // 所有在线机器列表
        result.put("totalOnline",  clawSessionRegistry.totalOnlineCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 向指定用户的插件发送消息
     *
     * POST /api/claw/send
     * Body:
     * {
     *   "userId":    "1",           // 必填
     *   "messageId": "msg_001",     // 必填
     *   "content":   "你好",        // 必填
     *   "clawId":    "claw_xxx",    // 可选，不填则发给第一个在线机器
     *   "context":   {},            // 可选
     *   "timeout":   30000          // 可选
     * }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        String userId    = (String) body.get("userId");
        String messageId = (String) body.get("messageId");
        String content   = (String) body.get("content");
        String clawId    = (String) body.get("clawId");

        Map<String, Object> result = new HashMap<>();

        if (userId == null || messageId == null || content == null) {
            result.put("success", false);
            result.put("message", "缺少必填字段：userId / messageId / content");
            return ResponseEntity.badRequest().body(result);
        }

        if (clawId == null || clawId.isBlank()) {
            result.put("success", false);
            result.put("message", "未指定目标设备 clawId");
            return ResponseEntity.ok(result);
        }

        messageService.saveUserMessage(messageId, Long.parseLong(userId), clawId, content);

        if (!clawSessionRegistry.isOnline(userId)) {
            result.put("success", false);
            result.put("online",  false);
            result.put("message", "用户 " + userId + " 的插件不在线");
            return ResponseEntity.ok(result);
        }

        JSONObject payload = new JSONObject();
        payload.put("type",      "request");
        payload.put("messageId", messageId);
        payload.put("content",   content);
        if (body.containsKey("context")) payload.put("context", body.get("context"));
        if (body.containsKey("timeout")) payload.put("timeout", body.get("timeout"));

        ClawSessionRegistry.ClawSession cs = clawSessionRegistry.getSession(userId, clawId);
        if (cs == null || !cs.getWsSession().isOpen()) {
            result.put("success", false);
            result.put("message", "指定的机器 " + clawId + " 不在线");
            return ResponseEntity.ok(result);
        }

        boolean sent = clawWebSocketHandler.sendToSession(cs.getWsSession(), payload.toJSONString());

        result.put("success",     sent);
        result.put("userId",      userId);
        result.put("messageId",   messageId);
        result.put("targetClawId", clawId);
        result.put("message",     sent ? "已下发给插件 " + clawId : "发送失败");
        return ResponseEntity.ok(result);
    }
}
