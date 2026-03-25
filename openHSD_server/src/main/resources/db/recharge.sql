-- HSDClaw 充值功能数据库脚本

-- 1. 用户表扩展：添加 OpenRouter Key 字段
ALTER TABLE users 
ADD COLUMN openrouter_key_hash VARCHAR(128) COMMENT 'OpenRouter API Key Hash' AFTER password_hash,
ADD COLUMN openrouter_key_label VARCHAR(64) COMMENT 'API Key 标识(脱敏显示)' AFTER openrouter_key_hash;

-- 2. 创建充值订单表
CREATE TABLE IF NOT EXISTS recharge_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    order_no VARCHAR(64) NOT NULL UNIQUE COMMENT '商户订单号',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    amount_cny DECIMAL(10,2) NOT NULL COMMENT '充值金额(人民币)',
    amount_usd DECIMAL(10,4) NOT NULL COMMENT '充值金额(美元)',
    exchange_rate DECIMAL(10,4) DEFAULT 8.0000 COMMENT '汇率',
    status TINYINT DEFAULT 0 COMMENT '0-待支付 1-已支付 2-已分配额度 3-已关闭',
    wechat_order_id VARCHAR(64) COMMENT '微信支付订单号',
    qrcode_url VARCHAR(512) COMMENT '支付二维码URL',
    paid_at DATETIME COMMENT '支付时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='充值订单表';
