package com.hsd.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.dto.AttachmentDTO;
import com.hsd.service.MessageService;
import com.hsd.service.MinioService;
import com.hsd.websocket.ClawWebSocketHandler;
import com.hsd.websocket.ClawSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * openHSD 插件消息 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/claw")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ClawController {

    private final ClawSessionRegistry   clawSessionRegistry;
    private final ClawWebSocketHandler  clawWebSocketHandler;
    private final MessageService        messageService;
    private final MinioService          minioService;

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
        result.put("clawList",     clawList);
        result.put("totalOnline",  clawSessionRegistry.totalOnlineCount());
        return ResponseEntity.ok(result);
    }

    /**
     * 向指定用户的插件发送消息
     *
     * POST /api/claw/send
     * Body:
     * {
     *   "userId":      "1",            // 必填
     *   "messageId":   "msg_001",      // 必填
     *   "content":     "你好",         // 可为空字符串（纯图片消息）
     *   "clawId":      "claw_xxx",     // 必填
     *   "attachments": [               // 可选
     *     { "base64": "data:image/png;base64,...", "name": "img.png", "type": "image/png" }
     *   ]
     * }
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@RequestBody Map<String, Object> body) {
        String userId    = (String) body.get("userId");
        String messageId = (String) body.get("messageId");
        String content   = (String) body.get("content");
        String clawId    = (String) body.get("clawId");

        Map<String, Object> result = new HashMap<>();

        if (userId == null || messageId == null) {
            result.put("success", false);
            result.put("message", "缺少必填字段：userId / messageId");
            return ResponseEntity.badRequest().body(result);
        }

        // content 允许为空字符串（纯图片消息）
        if (content == null) content = "";

        if (clawId == null || clawId.isBlank()) {
            result.put("success", false);
            result.put("message", "未指定目标设备 clawId");
            return ResponseEntity.ok(result);
        }

        // ======== 处理附件：base64 → MinIO ========
        List<?> rawAttachments = body.containsKey("attachments")
                ? (List<?>) body.get("attachments")
                : null;

        String attachmentsJson = null;

        if (rawAttachments != null && !rawAttachments.isEmpty()) {
            List<AttachmentDTO> uploadedList = new ArrayList<>();

            for (int i = 0; i < rawAttachments.size(); i++) {
                Object rawItem = rawAttachments.get(i);
                if (!(rawItem instanceof Map)) continue;

                @SuppressWarnings("unchecked")
                Map<String, Object> item = (Map<String, Object>) rawItem;

                String base64 = (String) item.get("base64");
                String name   = (String) item.getOrDefault("name", "image.png");
                String type   = (String) item.getOrDefault("type", "image/png");

                if (base64 == null || base64.isBlank()) continue;

                try {
                    String objectKey = minioService.uploadBase64Image(
                            base64, userId, clawId, messageId, i, name);
                    uploadedList.add(new AttachmentDTO(objectKey, null, name, type));
                    log.info("[ClawController] 图片上传成功：messageId={}, index={}, objectKey={}",
                            messageId, i, objectKey);
                } catch (Exception e) {
                    log.error("[ClawController] 图片上传 MinIO 失败，整条消息中止：messageId={}, index={}, error={}",
                            messageId, i, e.getMessage());
                    result.put("success", false);
                    result.put("message", "图片上传失败，消息未发送：" + e.getMessage());
                    return ResponseEntity.ok(result);
                }
            }

            if (!uploadedList.isEmpty()) {
                attachmentsJson = JSON.toJSONString(uploadedList);
            }
        }

        // ======== 保存用户消息到数据库（content + attachmentsJson） ========
        messageService.saveUserMessage(messageId, Long.parseLong(userId), clawId, content, attachmentsJson);

        // ======== 检查插件是否在线 ========
        if (!clawSessionRegistry.isOnline(userId)) {
            result.put("success", false);
            result.put("online",  false);
            result.put("message", "用户 " + userId + " 的插件不在线");
            return ResponseEntity.ok(result);
        }

        // ======== 构造转发给插件的 payload（附件保留原始 base64，插件无需改动） ========
        JSONObject payload = new JSONObject();
        payload.put("type",      "request");
        payload.put("messageId", messageId);
        payload.put("content",   content);
        if (body.containsKey("context")) payload.put("context", body.get("context"));
        if (body.containsKey("timeout")) payload.put("timeout", body.get("timeout"));
        // 原始 base64 附件直接透传给插件，插件侧不需要改动
        if (rawAttachments != null && !rawAttachments.isEmpty()) {
            payload.put("attachments", rawAttachments);
        }

        ClawSessionRegistry.ClawSession cs = clawSessionRegistry.getSession(userId, clawId);
        if (cs == null || !cs.getWsSession().isOpen()) {
            result.put("success", false);
            result.put("message", "指定的机器 " + clawId + " 不在线");
            return ResponseEntity.ok(result);
        }

        boolean sent = clawWebSocketHandler.sendToSession(cs.getWsSession(), payload.toJSONString());

        result.put("success",      sent);
        result.put("userId",       userId);
        result.put("messageId",    messageId);
        result.put("targetClawId", clawId);
        result.put("message",      sent ? "已下发给插件 " + clawId : "发送失败");
        return ResponseEntity.ok(result);
    }
}
