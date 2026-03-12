package com.hsd.service.impl;

import com.hsd.entity.Message;
import com.hsd.mapper.MessageMapper;
import com.hsd.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;

    @Override
    public List<Message> getHistory(Long userId, String clawId) {
        return messageMapper.findByUserIdAndClawId(userId, clawId);
    }

    @Override
    public void saveUserMessage(String messageId, Long userId, String clawId, String content, String attachmentsJson) {
        Message msg = new Message();
        msg.setMessageId(messageId);
        msg.setUserId(userId);
        msg.setClawId(clawId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setAttachments(attachmentsJson);
        msg.setStatus("completed");
        messageMapper.insert(msg);
        log.info("[MessageServiceImpl] 保存用户消息：messageId={}, userId={}, clawId={}, hasAttachments={}",
                messageId, userId, clawId, attachmentsJson != null);
    }

    @Override
    public void saveAssistantMessage(String messageId, Long userId, String clawId, String content, String status) {
        String existing = messageMapper.existsByMessageIdAndRole(messageId, "assistant");
        if (existing != null) {
            messageMapper.updateAssistantByMessageId(messageId, content, status);
            log.debug("[MessageServiceImpl] 更新助手消息：messageId={}, status={}", messageId, status);
        } else {
            Message msg = new Message();
            msg.setMessageId(messageId);
            msg.setUserId(userId);
            msg.setClawId(clawId);
            msg.setRole("assistant");
            msg.setContent(content);
            msg.setStatus(status);
            messageMapper.insert(msg);
            log.info("[MessageServiceImpl] 保存助手消息：messageId={}, userId={}, clawId={}", messageId, userId, clawId);
        }
    }

    @Override
    public void clearHistory(Long userId, String clawId) {
        int deleted = messageMapper.deleteByUserIdAndClawId(userId, clawId);
        log.info("[MessageServiceImpl] 清空历史：userId={}, clawId={}, deleted={}", userId, clawId, deleted);
    }
}
