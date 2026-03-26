package com.hsd.service.impl;

import com.hsd.entity.Message;
import com.hsd.mapper.MessageMapper;
import com.hsd.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 消息服务实现类
 * 
 * 管理用户与助手之间的消息记录，包括历史查询、消息保存和清空
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageMapper messageMapper;

    /**
     * 获取消息历史记录
     * 
     * @param userId 用户ID
     * @param clawId 设备/会话ID
     * @return 消息列表
     */
    @Override
    public List<Message> getHistory(Long userId, String clawId) {
        return messageMapper.findByUserIdAndClawId(userId, clawId);
    }

    /**
     * 保存用户消息
     * 
     * @param messageId 消息唯一ID
     * @param userId 用户ID
     * @param clawId 设备/会话ID
     * @param content 消息内容
     * @param attachmentsJson 附件JSON（可为null）
     */
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

    /**
     * 保存助手消息
     * 
     * 如果消息已存在则更新，否则新建
     * 
     * @param messageId 消息唯一ID
     * @param userId 用户ID
     * @param clawId 设备/会话ID
     * @param content 消息内容
     * @param status 消息状态（如 streaming、completed）
     */
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

    /**
     * 清空历史消息
     * 
     * @param userId 用户ID
     * @param clawId 设备/会话ID
     */
    @Override
    public void clearHistory(Long userId, String clawId) {
        int deleted = messageMapper.deleteByUserIdAndClawId(userId, clawId);
        log.info("[MessageServiceImpl] 清空历史：userId={}, clawId={}, deleted={}", userId, clawId, deleted);
    }

    /**
     * 保存文件推送消息
     * 
     * 用于保存助手向用户推送的文件消息
     * 
     * @param messageId 消息唯一ID
     * @param userId 用户ID
     * @param clawId 设备/会话ID
     * @param attachmentsJson 附件JSON
     */
    @Override
    public void saveFilePushMessage(String messageId, Long userId, String clawId, String attachmentsJson) {
        Message msg = new Message();
        msg.setMessageId(messageId);
        msg.setUserId(userId);
        msg.setClawId(clawId);
        msg.setRole("assistant");
        msg.setContent("");
        msg.setAttachments(attachmentsJson);
        msg.setStatus("completed");
        messageMapper.insert(msg);
        log.info("[MessageServiceImpl] 保存文件推送消息：messageId={}, userId={}, clawId={}", messageId, userId, clawId);
    }
}
