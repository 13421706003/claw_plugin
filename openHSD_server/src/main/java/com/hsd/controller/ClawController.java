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

        List<AttachmentDTO> resolvedAttachments = uploadAttachments(rawAttachments, userId, clawId, messageId, result);
        if (result.containsKey("success") && !(boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }

        // ======== 保存消息 ========
        messageService.saveUserMessage(messageId, Long.parseLong(userId), clawId, content,
                toAttachmentsJson(resolvedAttachments));

        // ======== 检查在线 ========
        if (!clawSessionRegistry.isOnline(userId)) {
            result.put("success", false);
            result.put("online",  false);
            result.put("message", "用户 " + userId + " 的插件不在线");
            return ResponseEntity.ok(result);
        }

        // ======== 构造 payload 并发送 ========
        JSONObject payload = buildPayload(messageId, content, body, resolvedAttachments);

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

        List<AttachmentDTO> resolvedAttachments = uploadAttachments(rawAttachments, userId, "broadcast", messageId, result);
        if (result.containsKey("success") && !(boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }

        // ======== 并发发送到每台设备 ========
        final String finalContent            = content;
        final String finalAttachmentsJson    = toAttachmentsJson(resolvedAttachments);
        final List<AttachmentDTO> finalAttachments = resolvedAttachments;

        List<CompletableFuture<BroadcastResult>> futures = sessions.stream()
                .map(cs -> CompletableFuture.supplyAsync(() -> {
                    String clawId   = cs.getClawId();
                    String subMsgId = messageId + "_" + clawId;
                    try {
                        // 存消息
                        messageService.saveUserMessage(
                                subMsgId, Long.parseLong(userId), clawId, finalContent, finalAttachmentsJson);

                        // 构造 payload 并发送
                        JSONObject payload = buildPayload(subMsgId, finalContent, body, finalAttachments);
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
     * 构造转发给插件的 payload JSON。
     * 图片附件优先补充 base64（并保留 url）以兼容仅支持内联图片的链路。
     */
    private JSONObject buildPayload(String messageId, String content,
                                     Map<String, Object> body,
                                     List<AttachmentDTO> resolvedAttachments) {
        JSONObject payload = new JSONObject();
        payload.put("type",      "request");
        payload.put("messageId", messageId);
        payload.put("content",   content);
        if (body.containsKey("context")) payload.put("context", body.get("context"));
        if (body.containsKey("timeout")) payload.put("timeout", body.get("timeout"));

        if (resolvedAttachments != null && !resolvedAttachments.isEmpty()) {
            // 构造插件协议格式：{ type, mimeType, url/base64, name, size }
            List<Map<String, Object>> pluginAttachments = new ArrayList<>();
            for (AttachmentDTO att : resolvedAttachments) {
                Map<String, Object> pa = new HashMap<>();
                String mimeType = att.getType() != null ? att.getType() : "application/octet-stream";
                // 按 MIME 判断大类型
                boolean isImage = mimeType.startsWith("image/");
                if (isImage) {
                    pa.put("type", "image");
                } else if (mimeType.startsWith("audio/")) {
                    pa.put("type", "audio");
                } else if (mimeType.startsWith("video/")) {
                    pa.put("type", "video");
                } else {
                    pa.put("type", "document");
                }
                pa.put("mimeType", mimeType);
                pa.put("url",      att.getUrl());
                pa.put("name",     att.getName());
                if (att.getObjectKey() != null) pa.put("objectKey", att.getObjectKey());
                if (att.getSize() != null) pa.put("size", att.getSize());

                if (isImage && att.getObjectKey() != null && !att.getObjectKey().isBlank()) {
                    try {
                        String dataUrl = minioService.objectToDataUrl(att.getObjectKey(), mimeType);
                        pa.put("base64", dataUrl);
                    } catch (Exception e) {
                        // 降级：保留 url 供插件继续尝试拉取
                        log.warn("[ClawController] 图片转base64失败，回退URL：messageId={}, objectKey={}, err={}",
                                messageId, att.getObjectKey(), e.getMessage());
                    }
                }
                pluginAttachments.add(pa);
            }
            payload.put("attachments", pluginAttachments);
        }
        return payload;
    }

    /**
     * 处理附件，兼容两种方式：
     *   1. objectKey 方式（文件已预上传）：直接拼接公开 URL
     *   2. base64 方式（图片粘贴）：上传到 MinIO 后拼接 URL
     *
     * 上传失败时会在 result 中设置 success=false 和 message
     *
     * @return 已解析的 AttachmentDTO 列表（含 url），无附件时返回 null
     */
    private List<AttachmentDTO> uploadAttachments(List<?> rawAttachments, String userId,
                                                   String clawId, String messageId,
                                                   Map<String, Object> result) {
        if (rawAttachments == null || rawAttachments.isEmpty()) return null;

        List<AttachmentDTO> resolvedList = new ArrayList<>();

        for (int i = 0; i < rawAttachments.size(); i++) {
            Object rawItem = rawAttachments.get(i);
            if (!(rawItem instanceof Map)) continue;

            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) rawItem;

            String objectKey = (String) item.get("objectKey");
            String base64    = (String) item.get("base64");
            String name      = (String) item.getOrDefault("name", "file");
            String type      = (String) item.getOrDefault("type", "application/octet-stream");
            Long   size      = item.get("size") instanceof Number n ? n.longValue() : null;

            if (objectKey != null && !objectKey.isBlank()) {
                // ── 方式 1：文件已预上传，直接生成 URL ─────────────────────
                String url = minioService.presignUrl(objectKey);
                resolvedList.add(new AttachmentDTO(objectKey, url, name, type, size));
                log.info("[ClawController] 已预上传附件：messageId={}, objectKey={}", messageId, objectKey);

            } else if (base64 != null && !base64.isBlank()) {
                // ── 方式 2：base64 图片，上传到 MinIO ──────────────────────
                try {
                    String key = minioService.uploadBase64Image(base64, userId, clawId, messageId, i, name);
                    String url = minioService.presignUrl(key);
                    resolvedList.add(new AttachmentDTO(key, url, name, type, size));
                    log.info("[ClawController] base64 图片上传成功：messageId={}, index={}, objectKey={}",
                            messageId, i, key);
                } catch (Exception e) {
                    log.error("[ClawController] base64 图片上传失败：messageId={}, index={}, error={}",
                            messageId, i, e.getMessage());
                    result.put("success", false);
                    result.put("message", "图片上传失败，消息未发送：" + e.getMessage());
                    return null;
                }
            }
            // 两个字段都没有则跳过
        }

        return resolvedList.isEmpty() ? null : resolvedList;
    }

    /**
     * 将 AttachmentDTO 列表序列化为存入数据库的 JSON 字符串
     * 只存 objectKey/name/type/size，不存 url
     */
    private String toAttachmentsJson(List<AttachmentDTO> list) {
        if (list == null || list.isEmpty()) return null;
        // 创建仅含持久化字段的副本
        List<AttachmentDTO> dbList = list.stream()
                .map(a -> new AttachmentDTO(a.getObjectKey(), null, a.getName(), a.getType(), a.getSize()))
                .toList();
        return JSON.toJSONString(dbList);
    }
}
