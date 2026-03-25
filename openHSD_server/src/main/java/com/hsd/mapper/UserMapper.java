package com.hsd.mapper;

import com.hsd.entity.User;
import org.apache.ibatis.annotations.*;

/**
 * 用户数据访问层接口
 * 
 * 提供用户表的 CRUD 操作，包括用户认证相关的查询
 * 以及 OpenRouter API Key 绑定功能。
 */
@Mapper
public interface UserMapper {

    /**
     * 根据用户名查询用户信息
     * 
     * @param username 用户名
     * @return 用户实体，不存在则返回 null
     */
    @Select("SELECT id, username, password_hash, created_at, openrouter_key, openrouter_key_label FROM users WHERE username = #{username} LIMIT 1")
    User findByUsername(String username);

    /**
     * 根据用户ID查询用户信息
     * 
     * @param id 用户ID
     * @return 用户实体，不存在则返回 null
     */
    @Select("SELECT id, username, password_hash, created_at, openrouter_key, openrouter_key_label FROM users WHERE id = #{id} LIMIT 1")
    User findById(Long id);

    /**
     * 插入新用户记录
     * 
     * @param username 用户名
     * @param passwordHash 密码哈希值
     * @return 影响行数
     */
    @Insert("INSERT INTO users (username, password_hash, created_at) VALUES (#{username}, #{passwordHash}, NOW())")
    int insert(@Param("username") String username, @Param("passwordHash") String passwordHash);

    /**
     * 更新用户的 OpenRouter API Key 绑定信息
     * 
     * @param userId 用户ID
     * @param openrouterKey 完整的 API Key（用于调用 OpenRouter API）
     * @param keyLabel API Key 的脱敏标识（用于前端显示）
     * @return 影响行数
     */
    @Update("UPDATE users SET openrouter_key = #{openrouterKey}, openrouter_key_label = #{keyLabel} WHERE id = #{userId}")
    int updateOpenRouterKey(@Param("userId") Long userId, @Param("openrouterKey") String openrouterKey, @Param("keyLabel") String keyLabel);
}
