-- 消息历史表
-- 用于持久化存储用户与 AI 的对话记录

CREATE TABLE IF NOT EXISTS messages (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_id VARCHAR(64) NOT NULL COMMENT '消息唯一标识',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    claw_id VARCHAR(128) NOT NULL COMMENT '设备ID (claw_xxx)',
    role VARCHAR(16) NOT NULL COMMENT '角色: user / assistant',
    content TEXT COMMENT '消息内容',
    status VARCHAR(16) DEFAULT 'completed' COMMENT '状态: completed / error / pending',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    
    INDEX idx_user_claw (user_id, claw_id),
    INDEX idx_message_id (message_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息历史表';
