package com.hsd.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.dto.AttachmentDTO;
import com.hsd.service.MessageService;
import com.hsd.service.MinioService;
import com.hsd.websocket.ClawWebSocketHandler;
import com.hsd.websocket.ClawSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * openHSD 插件消息 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/claw")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ClawController {

    private final ClawSessionRegistry   clawSessionRegistry;
    private final ClawWebSocketHandler  clawWebSocketHandler;
    private final MessageService        messageService;
    private final MinioService          minioService;
    private final Executor              broadcastExecutor;

    public ClawController(ClawSessionRegistry clawSessionRegistry,
                          ClawWebSocketHandler clawWebSocketHandler,
                          MessageService messageService,
                          MinioService minioService,
                          @Qualifier("broadcastExecutor") Executor broadcastExecutor) {
        this.clawSessionRegistry  = clawSessionRegistry;
        this.clawWebSocketHandler = clawWebSocketHandler;
        this.messageService       = messageService;
        this.minioService         = minioService;
        this.broadcastExecutor    = broadcastExecutor;
    }

    // ================================================================
    // 内部结果类
    // ================================================================

    private record BroadcastResult(String clawId, String subMsgId, boolean sent, String error) {}

    // ================================================================
    // 查询设备状态
    // ================================================================

    /**
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

    // ================================================================
    // 单设备发送
    // ================================================================

    /**
     * POST /api/claw/send
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

        if (content == null) content = "";

        if (clawId == null || clawId.isBlank()) {
            result.put("success", false);
            result.put("message", "未指定目标设备 clawId");
            return ResponseEntity.ok(result);
        }

        // ======== 处理附件 ========
        List<?> rawAttachments = body.containsKey("attachments")
                ? (List<?>) body.get("attachments") : null;

        String attachmentsJson = uploadAttachments(rawAttachments, userId, clawId, messageId, result);
        if (result.containsKey("success") && !(boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }

        // ======== 保存消息 ========
        messageService.saveUserMessage(messageId, Long.parseLong(userId), clawId, content, attachmentsJson);

        // ======== 检查在线 ========
        if (!clawSessionRegistry.isOnline(userId)) {
            result.put("success", false);
            result.put("online",  false);
            result.put("message", "用户 " + userId + " 的插件不在线");
            return ResponseEntity.ok(result);
        }

        // ======== 构造 payload 并发送 ========
        JSONObject payload = buildPayload(messageId, content, body, rawAttachments);

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

    // ================================================================
    // 广播发送（并发）
    // ================================================================

    /**
     * POST /api/claw/broadcast
     *
     * 向用户所有在线插件并发广播消息
     * 每台机器分配独立的子 messageId: {messageId}_{clawId}
     * 图片只上传一次 MinIO，所有机器共享
     * 单台设备超时 5 秒
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@RequestBody Map<String, Object> body) {
        String userId    = (String) body.get("userId");
        String messageId = (String) body.get("messageId");
        String content   = (String) body.get("content");

        Map<String, Object> result = new HashMap<>();

        if (userId == null || messageId == null) {
            result.put("success", false);
            result.put("message", "缺少必填字段：userId / messageId");
            return ResponseEntity.badRequest().body(result);
        }

        if (content == null) content = "";

        // ======== 获取所有在线设备 ========
        List<ClawSessionRegistry.ClawSession> sessions = clawSessionRegistry.getSessions(userId);
        if (sessions.isEmpty()) {
            result.put("success", false);
            result.put("online",  false);
            result.put("message", "用户 " + userId + " 无在线插件");
            return ResponseEntity.ok(result);
        }

        // ======== 处理附件（主线程上传一次，共享 objectKey） ========
        List<?> rawAttachments = body.containsKey("attachments")
                ? (List<?>) body.get("attachments") : null;

        String attachmentsJson = uploadAttachments(rawAttachments, userId, "broadcast", messageId, result);
        if (result.containsKey("success") && !(boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }

        // ======== 并发发送到每台设备 ========
        final String finalContent        = content;
        final String finalAttachmentsJson = attachmentsJson;

        List<CompletableFuture<BroadcastResult>> futures = sessions.stream()
                .map(cs -> CompletableFuture.supplyAsync(() -> {
                    String clawId   = cs.getClawId();
                    String subMsgId = messageId + "_" + clawId;
                    try {
                        // 存消息
                        messageService.saveUserMessage(
                                subMsgId, Long.parseLong(userId), clawId, finalContent, finalAttachmentsJson);

                        // 构造 payload 并发送
                        JSONObject payload = buildPayload(subMsgId, finalContent, body, rawAttachments);
                        boolean sent = clawWebSocketHandler.sendToSession(
                                cs.getWsSession(), payload.toJSONString());

                        log.info("[Broadcast] clawId={}, subMsgId={}, sent={}", clawId, subMsgId, sent);
                        return new BroadcastResult(clawId, subMsgId, sent, null);
                    } catch (Exception e) {
                        log.error("[Broadcast] clawId={} 失败：{}", clawId, e.getMessage());
                        return new BroadcastResult(clawId, subMsgId, false, e.getMessage());
                    }
                }, broadcastExecutor).orTimeout(5, TimeUnit.SECONDS))
                .toList();

        // ======== 等待全部完成，收集结果 ========
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<BroadcastResult> results = futures.stream()
                .map(f -> {
                    try {
                        return f.join();
                    } catch (Exception e) {
                        return new BroadcastResult("unknown", "", false, "超时");
                    }
                })
                .toList();

        List<String> sentClawIds    = results.stream().filter(BroadcastResult::sent)
                .map(BroadcastResult::clawId).toList();
        List<String> sentMessageIds = results.stream().filter(BroadcastResult::sent)
                .map(BroadcastResult::subMsgId).toList();
        List<String> failedClawIds  = results.stream().filter(r -> !r.sent())
                .map(BroadcastResult::clawId).toList();

        result.put("success",        !sentClawIds.isEmpty());
        result.put("userId",         userId);
        result.put("messageId",      messageId);
        result.put("sentClawIds",    sentClawIds);
        result.put("sentMessageIds", sentMessageIds);
        result.put("failedClawIds",  failedClawIds);
        result.put("message",        "已广播给 " + sentClawIds.size() + " 台设备"
                + (failedClawIds.isEmpty() ? "" : "，" + failedClawIds.size() + " 台失败"));
        return ResponseEntity.ok(result);
    }

    // ================================================================
    // 私有方法
    // ================================================================

    /**
     * 构造转发给插件的 payload JSON
     */
    private JSONObject buildPayload(String messageId, String content,
                                     Map<String, Object> body, List<?> rawAttachments) {
        JSONObject payload = new JSONObject();
        payload.put("type",      "request");
        payload.put("messageId", messageId);
        payload.put("content",   content);
        if (body.containsKey("context")) payload.put("context", body.get("context"));
        if (body.containsKey("timeout")) payload.put("timeout", body.get("timeout"));
        if (rawAttachments != null && !rawAttachments.isEmpty()) {
            payload.put("attachments", rawAttachments);
        }
        return payload;
    }

    /**
     * 处理附件：base64 → MinIO 上传，返回 attachmentsJson
     * 上传失败时会在 result 中设置 success=false 和 message
     *
     * @return attachmentsJson 字符串，无附件时返回 null
     */
    private String uploadAttachments(List<?> rawAttachments, String userId,
                                      String clawId, String messageId,
                                      Map<String, Object> result) {
        if (rawAttachments == null || rawAttachments.isEmpty()) return null;

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
                log.error("[ClawController] 图片上传失败：messageId={}, index={}, error={}",
                        messageId, i, e.getMessage());
                result.put("success", false);
                result.put("message", "图片上传失败，消息未发送：" + e.getMessage());
                return null;
            }
        }

        return uploadedList.isEmpty() ? null : JSON.toJSONString(uploadedList);
    }
}
