package com.hsd.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.hsd.dto.AttachmentDTO;
import com.hsd.dto.ClawBroadcastRequest;
import com.hsd.dto.ClawSendRequest;
import com.hsd.service.MessageService;
import com.hsd.service.MinioService;
import com.hsd.websocket.ClawWebSocketHandler;
import com.hsd.websocket.ClawSessionRegistry;
import com.hsd.websocket.MessageTabRouter;
import jakarta.validation.Valid;
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
 * 
 * 提供插件通信相关的 RESTful API，包括：
 * - 查询设备在线状态
 * - 单设备消息发送
 * - 多设备广播消息
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
    private final MessageTabRouter      messageTabRouter;
    private final Executor              broadcastExecutor;

    public ClawController(ClawSessionRegistry clawSessionRegistry,
                          ClawWebSocketHandler clawWebSocketHandler,
                          MessageService messageService,
                          MinioService minioService,
                          MessageTabRouter messageTabRouter,
                          @Qualifier("broadcastExecutor") Executor broadcastExecutor) {
        this.clawSessionRegistry  = clawSessionRegistry;
        this.clawWebSocketHandler = clawWebSocketHandler;
        this.messageService       = messageService;
        this.minioService         = minioService;
        this.messageTabRouter     = messageTabRouter;
        this.broadcastExecutor    = broadcastExecutor;
    }

    private record BroadcastResult(String clawId, String subMsgId, boolean sent, String error) {}

    /**
     * 查询设备在线状态
     * 
     * 获取指定用户的所有插件设备在线状态列表。
     * 
     * @param userId 用户ID
     * @return 设备状态信息
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
     * 单设备发送消息
     * 
     * 向指定设备发送消息，支持文本和附件。
     * 
     * @param request 发送请求参数
     * @return 发送结果
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> send(@Valid @RequestBody ClawSendRequest request) {
        String userId    = request.getUserId();
        String messageId = request.getMessageId();
        String content   = request.getContent();
        String clawId    = request.getClawId();

        Map<String, Object> result = new HashMap<>();

        if (clawId == null || clawId.isBlank()) {
            result.put("success", false);
            result.put("message", "未指定目标设备 clawId");
            return ResponseEntity.ok(result);
        }

        List<AttachmentDTO> resolvedAttachments = uploadAttachments(request.getAttachments(), userId, clawId, messageId, result);
        if (result.containsKey("success") && !(boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }

        messageService.saveUserMessage(messageId, Long.parseLong(userId), clawId, content,
                toAttachmentsJson(resolvedAttachments));

        if (!clawSessionRegistry.isOnline(userId)) {
            result.put("success", false);
            result.put("online",  false);
            result.put("message", "用户 " + userId + " 的插件不在线");
            return ResponseEntity.ok(result);
        }

        JSONObject payload = buildPayload(messageId, content, request.getContext(), request.getTimeout(), resolvedAttachments);

        ClawSessionRegistry.ClawSession cs = clawSessionRegistry.getSession(userId, clawId);
        if (cs == null || !cs.getWsSession().isOpen()) {
            result.put("success", false);
            result.put("message", "指定的机器 " + clawId + " 不在线");
            return ResponseEntity.ok(result);
        }

        boolean sent = clawWebSocketHandler.sendToSession(cs.getWsSession(), payload.toJSONString());

        // 注册消息路由：记录该消息由哪个标签页发起，响应时精准推回
        if (sent) {
            String tabId = request.getTabId();
            if (tabId != null && !tabId.isBlank()) {
                messageTabRouter.register(messageId, tabId);
            }
        }

        result.put("success",      sent);
        result.put("userId",       userId);
        result.put("messageId",    messageId);
        result.put("targetClawId", clawId);
        result.put("message",      sent ? "已下发给插件 " + clawId : "发送失败");
        return ResponseEntity.ok(result);
    }

    /**
     * 广播消息
     * 
     * 向用户所有在线插件并发广播消息，每台设备分配独立的子 messageId。
     * 
     * @param request 广播请求参数
     * @return 广播结果
     */
    @PostMapping("/broadcast")
    public ResponseEntity<Map<String, Object>> broadcast(@Valid @RequestBody ClawBroadcastRequest request) {
        String userId    = request.getUserId();
        String messageId = request.getMessageId();
        String content   = request.getContent();

        Map<String, Object> result = new HashMap<>();

        List<ClawSessionRegistry.ClawSession> sessions = clawSessionRegistry.getSessions(userId);
        if (sessions.isEmpty()) {
            result.put("success", false);
            result.put("online",  false);
            result.put("message", "用户 " + userId + " 无在线插件");
            return ResponseEntity.ok(result);
        }

        List<AttachmentDTO> resolvedAttachments = uploadAttachments(request.getAttachments(), userId, "broadcast", messageId, result);
        if (result.containsKey("success") && !(boolean) result.get("success")) {
            return ResponseEntity.ok(result);
        }

        final String finalContent            = content;
        final String finalAttachmentsJson    = toAttachmentsJson(resolvedAttachments);
        final List<AttachmentDTO> finalAttachments = resolvedAttachments;
        final Map<String, Object> finalContext = request.getContext();
        final Integer finalTimeout           = request.getTimeout();
        final String finalTabId              = request.getTabId();

        List<CompletableFuture<BroadcastResult>> futures = sessions.stream()
                .map(cs -> CompletableFuture.supplyAsync(() -> {
                    String clawId   = cs.getClawId();
                    String subMsgId = messageId + "_" + clawId;
                    try {
                        messageService.saveUserMessage(
                                subMsgId, Long.parseLong(userId), clawId, finalContent, finalAttachmentsJson);

                        JSONObject payload = buildPayload(subMsgId, finalContent, finalContext, finalTimeout, finalAttachments);
                        boolean sent = clawWebSocketHandler.sendToSession(
                                cs.getWsSession(), payload.toJSONString());

                        // 注册消息路由：广播的所有子消息均路由回同一个发起标签页
                        if (sent && finalTabId != null && !finalTabId.isBlank()) {
                            messageTabRouter.register(subMsgId, finalTabId);
                        }

                        log.info("[Broadcast] clawId={}, subMsgId={}, sent={}", clawId, subMsgId, sent);
                        return new BroadcastResult(clawId, subMsgId, sent, null);
                    } catch (Exception e) {
                        log.error("[Broadcast] clawId={} 失败：{}", clawId, e.getMessage());
                        return new BroadcastResult(clawId, subMsgId, false, e.getMessage());
                    }
                }, broadcastExecutor).orTimeout(5, TimeUnit.SECONDS))
                .toList();

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

    /**
     * 构造转发给插件的 payload JSON
     */
    private JSONObject buildPayload(String messageId, String content,
                                     Map<String, Object> context,
                                     Integer timeout,
                                     List<AttachmentDTO> resolvedAttachments) {
        JSONObject payload = new JSONObject();
        payload.put("type",      "request");
        payload.put("messageId", messageId);
        payload.put("content",   content);
        if (context != null) payload.put("context", context);
        if (timeout != null) payload.put("timeout", timeout);

        if (resolvedAttachments != null && !resolvedAttachments.isEmpty()) {
            List<Map<String, Object>> pluginAttachments = new ArrayList<>();
            for (AttachmentDTO att : resolvedAttachments) {
                Map<String, Object> pa = new HashMap<>();
                String mimeType = att.getType() != null ? att.getType() : "application/octet-stream";
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
     * 处理附件，兼容 objectKey 和 base64 两种方式
     */
    private List<AttachmentDTO> uploadAttachments(List<AttachmentDTO> rawAttachments, String userId,
                                                    String clawId, String messageId,
                                                    Map<String, Object> result) {
        if (rawAttachments == null || rawAttachments.isEmpty()) return null;

        List<AttachmentDTO> resolvedList = new ArrayList<>();

        for (int i = 0; i < rawAttachments.size(); i++) {
            AttachmentDTO item = rawAttachments.get(i);

            String objectKey = item.getObjectKey();
            String base64    = item.getUrl();
            String name      = item.getName() != null ? item.getName() : "file";
            String type      = item.getType() != null ? item.getType() : "application/octet-stream";
            Long   size      = item.getSize();

            if (objectKey != null && !objectKey.isBlank()) {
                String url = minioService.presignUrl(objectKey);
                resolvedList.add(new AttachmentDTO(objectKey, url, name, type, size));
                log.info("[ClawController] 已预上传附件：messageId={}, objectKey={}", messageId, objectKey);

            } else if (base64 != null && !base64.isBlank() && base64.startsWith("data:")) {
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
        }

        return resolvedList.isEmpty() ? null : resolvedList;
    }

    /**
     * 将 AttachmentDTO 列表序列化为存入数据库的 JSON 字符串
     */
    private String toAttachmentsJson(List<AttachmentDTO> list) {
        if (list == null || list.isEmpty()) return null;
        List<AttachmentDTO> dbList = list.stream()
                .map(a -> new AttachmentDTO(a.getObjectKey(), null, a.getName(), a.getType(), a.getSize()))
                .toList();
        return JSON.toJSONString(dbList);
    }
}
