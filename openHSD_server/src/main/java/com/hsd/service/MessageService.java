package com.hsd.service;

import com.hsd.entity.Message;

import java.util.List;

public interface MessageService {

    List<Message> getHistory(Long userId, String clawId);

    /**
     * @param attachmentsJson 附件 JSON 字符串（objectKey 数组），无附件传 null
     */
    void saveUserMessage(String messageId, Long userId, String clawId, String content, String attachmentsJson);

    void saveAssistantMessage(String messageId, Long userId, String clawId, String content, String status);

    void clearHistory(Long userId, String clawId);
}
