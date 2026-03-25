package com.hsd.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 
 * 表示系统用户的基本信息。
 */
@Data
public class User {
    /** 用户主键ID */
    private Long id;
    
    /** 用户名 */
    private String username;
    
    /** 密码哈希值 */
    private String passwordHash;
    
    /** 创建时间 */
    private LocalDateTime createdAt;
    
    /** OpenRouter API Key（完整Key，加密存储） */
    private String openrouterKey;
    
    /** API Key 脱敏标签（用于前端显示） */
    private String openrouterKeyLabel;
    
    /** OpenRouter Key Hash（用于Management API更新额度） */
    private String openrouterKeyHash;
}
