package com.hsd.service;

import com.hsd.entity.Message;

import java.util.List;

public interface MessageService {

    List<Message> getHistory(Long userId, String clawId);

    void saveUserMessage(String messageId, Long userId, String clawId, String content);

    void saveAssistantMessage(String messageId, Long userId, String clawId, String content, String status);

    void clearHistory(Long userId, String clawId);
}
