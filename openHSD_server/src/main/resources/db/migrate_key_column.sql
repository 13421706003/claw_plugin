-- 数据库迁移：修改 openrouter_key_hash 为 openrouter_key
-- 注意：请手动执行此 SQL

-- 1. 重命名列（如果数据库中已有 openrouter_key_hash 列）
ALTER TABLE users CHANGE COLUMN openrouter_key_hash openrouter_key VARCHAR(256) COMMENT 'OpenRouter API Key';

-- 2. 如果是新建表，使用以下完整 SQL
-- 用户表扩展
ALTER TABLE users 
ADD COLUMN openrouter_key VARCHAR(256) COMMENT 'OpenRouter API Key' AFTER password_hash,
ADD COLUMN openrouter_key_label VARCHAR(64) COMMENT 'API Key 标识(脱敏显示)' AFTER openrouter_key;
