package com.hsd.mapper;

import com.hsd.entity.Message;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MessageMapper {

    @Select("SELECT id, message_id, user_id, claw_id, role, content, attachments, status, created_at " +
            "FROM messages WHERE user_id = #{userId} AND claw_id = #{clawId} " +
            "ORDER BY created_at ASC")
    List<Message> findByUserIdAndClawId(@Param("userId") Long userId, @Param("clawId") String clawId);

    @Insert("INSERT INTO messages (message_id, user_id, claw_id, role, content, attachments, status, created_at) " +
            "VALUES (#{messageId}, #{userId}, #{clawId}, #{role}, #{content}, #{attachments}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Message message);

    @Update("UPDATE messages SET content = #{content}, status = #{status} WHERE message_id = #{messageId} AND role = 'assistant'")
    int updateAssistantByMessageId(@Param("messageId") String messageId, @Param("content") String content, @Param("status") String status);

    @Select("SELECT message_id FROM messages WHERE message_id = #{messageId} AND role = #{role} LIMIT 1")
    String existsByMessageIdAndRole(@Param("messageId") String messageId, @Param("role") String role);

    @Delete("DELETE FROM messages WHERE user_id = #{userId} AND claw_id = #{clawId}")
    int deleteByUserIdAndClawId(@Param("userId") Long userId, @Param("clawId") String clawId);
}
