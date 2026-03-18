package com.hsd.controller;

import com.alibaba.fastjson2.JSONObject;
import com.hsd.service.MessageService;
import com.hsd.websocket.WebSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/push")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequiredArgsConstructor
public class PushController {

    private final WebSessionRegistry webSessionRegistry;
    private final MessageService messageService;

    @PostMapping("/file")
    public Map<String, Object> pushFile(@RequestBody Map<String, Object> body) {
        String userId   = (String) body.get("userId");
        String clawId   = (String) body.get("clawId");
        String fileUrl  = (String) body.get("fileUrl");
        String fileName = (String) body.getOrDefault("fileName", "file");
        String fileType = (String) body.getOrDefault("fileType", "application/octet-stream");
        Long fileSize   = body.get("fileSize") instanceof Number n ? n.longValue() : null;

        if (userId == null || clawId == null || fileUrl == null) {
            log.warn("[PushController] 缺少必填字段：userId={}, clawId={}, fileUrl={}", userId, clawId, fileUrl);
            return Map.of("success", false, "message", "缺少必填字段：userId / clawId / fileUrl");
        }

        // 生成消息 ID
        String messageId = "file_push_" + System.currentTimeMillis();

        // 构造附件 JSON
        JSONObject attachment = new JSONObject();
        attachment.put("url", fileUrl);
        attachment.put("name", fileName);
        attachment.put("type", fileType);
        attachment.put("size", fileSize);
        String attachmentsJson = "[" + attachment.toJSONString() + "]";

        // 保存到数据库
        try {
            messageService.saveFilePushMessage(messageId, Long.parseLong(userId), clawId, attachmentsJson);
        } catch (Exception e) {
            log.error("[PushController] 保存文件推送消息失败：{}", e.getMessage());
        }

        // 推送给前端
        JSONObject msg = new JSONObject();
        msg.put("type", "file_push");
        msg.put("messageId", messageId);
        msg.put("clawId", clawId);
        msg.put("fileUrl", fileUrl);
        msg.put("fileName", fileName);
        msg.put("fileType", fileType);
        msg.put("fileSize", fileSize);
        msg.put("timestamp", System.currentTimeMillis());

        boolean sent = webSessionRegistry.pushToUser(userId, msg.toJSONString());

        log.info("[PushController] 文件推送：userId={}, clawId={}, fileUrl={}, sent={}",
                userId, clawId, fileUrl, sent);

        return Map.of(
            "success", sent,
            "userId", userId,
            "clawId", clawId,
            "messageId", messageId,
            "message", sent ? "已推送给用户" : "用户未在线"
        );
    }
}
