-- 添加 openrouter_key_hash 字段
-- 用于存储OpenRouter API Key的hash值，用于Management API更新额度

ALTER TABLE users ADD COLUMN openrouter_key_hash VARCHAR(64) DEFAULT NULL COMMENT 'OpenRouter Key Hash值，用于Management API操作';
