package com.hsd.controller;

import com.hsd.entity.Message;
import com.hsd.service.MessageService;
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

    @GetMapping
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam Long userId,
            @RequestParam String clawId) {
        List<Message> messages = messageService.getHistory(userId, clawId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("userId", userId);
        result.put("clawId", clawId);
        result.put("messages", messages);
        result.put("count", messages.size());
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
