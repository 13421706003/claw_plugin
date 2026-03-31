package com.hsd.controller;

import com.alibaba.fastjson2.JSONObject;
import com.hsd.dto.PushFileRequest;
import com.hsd.service.MessageService;
import com.hsd.websocket.WebSessionRegistry;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 推送控制器
 * 
 * 提供消息推送相关的 RESTful API，包括：
 * - 文件推送通知
 */
@Slf4j
@RestController
@RequestMapping("/api/push")
@CrossOrigin(origins = "*", allowedHeaders = "*")
@RequiredArgsConstructor
public class PushController {

    private final WebSessionRegistry webSessionRegistry;
    private final MessageService messageService;

    /**
     * 推送文件通知
     * 
     * 向前端推送文件消息，用于插件向后端报告文件传输结果。
     * 
     * @param request 文件推送请求参数
     * @return 推送结果
     */
    @PostMapping("/file")
    public Map<String, Object> pushFile(@Valid @RequestBody PushFileRequest request) {
        String userId   = request.getUserId();
        String clawId   = request.getClawId();
        String fileUrl  = request.getFileUrl();
        String fileName = request.getFileName();
        String fileType = request.getFileType();
        Long fileSize   = request.getFileSize();

        String messageId = "file_push_" + System.currentTimeMillis();

        JSONObject attachment = new JSONObject();
        attachment.put("url", fileUrl);
        attachment.put("name", fileName);
        attachment.put("type", fileType);
        attachment.put("size", fileSize);
        String attachmentsJson = "[" + attachment.toJSONString() + "]";

        try {
            messageService.saveFilePushMessage(messageId, Long.parseLong(userId), clawId, attachmentsJson);
        } catch (Exception e) {
            log.error("[PushController] 保存文件推送消息失败：{}", e.getMessage());
        }

        JSONObject msg = new JSONObject();
        msg.put("type", "file_push");
        msg.put("messageId", messageId);
        msg.put("clawId", clawId);
        msg.put("fileUrl", fileUrl);
        msg.put("fileName", fileName);
        msg.put("fileType", fileType);
        msg.put("fileSize", fileSize);
        msg.put("timestamp", System.currentTimeMillis());

        int sentCount = webSessionRegistry.pushToUser(userId, msg.toJSONString());
        boolean sent  = sentCount > 0;

        log.info("[PushController] 文件推送：userId={}, clawId={}, fileUrl={}, sentTabs={}",
                userId, clawId, fileUrl, sentCount);

        return Map.of(
            "success", sent,
            "userId", userId,
            "clawId", clawId,
            "messageId", messageId,
            "message", sent ? "已推送给用户" : "用户未在线"
        );
    }
}
