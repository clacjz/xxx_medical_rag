-- V1__update_audit_logs_table.sql
-- 数据库审计日志表结构更新

-- 添加审计级别字段
ALTER TABLE audit_logs ADD COLUMN IF NOT EXISTS level VARCHAR(20) DEFAULT 'INFO';

-- 更新现有记录的默认级别
UPDATE audit_logs SET level = 'INFO' WHERE level IS NULL;

-- 确保 action 字段有默认值（如果之前没有的话）
ALTER TABLE audit_logs ALTER COLUMN action SET DEFAULT 'LOGIN';

-- 添加注释（PostgreSQL）
COMMENT ON COLUMN audit_logs.level IS '审计级别: INFO, WARNING, SECURITY, CRITICAL';

-- 创建索引以优化按级别查询
CREATE INDEX IF NOT EXISTS idx_audit_level ON audit_logs(level);

-- 创建组合索引以优化常见查询
CREATE INDEX IF NOT EXISTS idx_audit_user_action_level ON audit_logs(user_id, action, level);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp_level ON audit_logs(timestamp, level);
