-- ============================================================
-- 数据库专用低权账号（S-5 安全加固）
-- 目的：后端与 AI 服务不再使用 root 直连，改为专用账号 sms_app，
--       仅授予 student_management 库的 CRUD 权限（无 DDL / 无全局权限）。
-- 用法：以 root 执行本脚本后，将 .env / 环境变量中的数据库凭据改为 sms_app。
-- ============================================================

CREATE USER IF NOT EXISTS 'sms_app'@'localhost' IDENTIFIED BY 'SmsApp@2026!ChangeMe';
GRANT SELECT, INSERT, UPDATE, DELETE ON student_management.* TO 'sms_app'@'localhost';
-- 若 AI 服务只做只读查询，可额外建只读账号（可选）：
-- CREATE USER IF NOT EXISTS 'sms_ai_ro'@'localhost' IDENTIFIED BY 'SmsAiRo@2026!ChangeMe';
-- GRANT SELECT ON student_management.* TO 'sms_ai_ro'@'localhost';
FLUSH PRIVILEGES;

-- 说明：
-- 1. 密码为占位符，部署时必须更换（生产禁止使用示例密码）。
-- 2. Spring 后端通过环境变量 DB_USERNAME / DB_PASSWORD 注入；
--    AI 服务通过 backend-ai/.env 的 MYSQL_USER / MYSQL_PASSWORD 注入。
-- 3. 如需远程访问，将 'localhost' 改为 '%' 或具体网段，并限制来源 IP。
