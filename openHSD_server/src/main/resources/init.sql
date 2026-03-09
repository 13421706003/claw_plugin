-- 创建数据库
CREATE DATABASE IF NOT EXISTS openhsd DEFAULT CHARACTER SET utf8mb4;
USE openhsd;

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    password_hash VARCHAR(255) NOT NULL        COMMENT 'MD5 密码哈希',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始用户（密码 123456 的 MD5）
INSERT IGNORE INTO users (username, password_hash) VALUES
('admin', 'e10adc3949ba59abbe56e057f20f883e'),
('user1', 'e10adc3949ba59abbe56e057f20f883e');
