package com.hsd.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.hsd.dto.AttachmentDTO;
import com.hsd.entity.Message;
import com.hsd.service.MessageService;
import com.hsd.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class MessageController {

    private final MessageService messageService;
    private final MinioService   minioService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam Long userId,
            @RequestParam String clawId) {

        List<Message> messages = messageService.getHistory(userId, clawId);

        // 对每条消息的 attachments 字段：解析 objectKey → 生成预签名 URL → 回写
        for (Message msg : messages) {
            String attachmentsJson = msg.getAttachments();
            if (attachmentsJson == null || attachmentsJson.isBlank()) continue;

            try {
                List<AttachmentDTO> attachments = JSON.parseObject(
                        attachmentsJson, new TypeReference<List<AttachmentDTO>>() {});

                for (AttachmentDTO att : attachments) {
                    if (att.getObjectKey() != null && !att.getObjectKey().isBlank()) {
                        att.setUrl(minioService.presignUrl(att.getObjectKey()));
                    }
                }

                // 将填充了 url 的附件列表重新序列化回 attachments 字段，供前端使用
                msg.setAttachments(JSON.toJSONString(attachments));
            } catch (Exception e) {
                log.error("[MessageController] 解析附件失败：messageId={}, error={}",
                        msg.getMessageId(), e.getMessage());
                // 解析失败不影响其余消息，置为 null 继续
                msg.setAttachments(null);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success",  true);
        result.put("userId",   userId);
        result.put("clawId",   clawId);
        result.put("messages", messages);
        result.put("count",    messages.size());
        return ResponseEntity.ok(result);
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> clearHistory(
            @RequestParam Long userId,
            @RequestParam String clawId) {
        messageService.clearHistory(userId, clawId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "历史消息已清空");
        return ResponseEntity.ok(result);
    }
}
