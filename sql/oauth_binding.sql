-- ============================================================
-- 学生信息管理系统 - GitHub OAuth 登录绑定关系表
-- 需在 MySQL 手动执行一次（独立于 init.sql，不破坏既有数据）
-- ============================================================

CREATE TABLE IF NOT EXISTS oauth_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_no VARCHAR(20) NOT NULL COMMENT '系统账号（工号/学号，对应 staff.staff_no / student.student_no）',
    provider VARCHAR(20) NOT NULL COMMENT 'OAuth 提供方：github',
    provider_uid VARCHAR(64) NOT NULL COMMENT 'GitHub 用户唯一 id',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_uid (provider, provider_uid),
    UNIQUE KEY uk_user_provider (user_no, provider)
) COMMENT 'OAuth 登录绑定关系表';
