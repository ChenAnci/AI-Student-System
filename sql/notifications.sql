-- ============================================================
-- 站内通知系统
-- MySQL 8.0 | 库名: student_management
-- ============================================================
USE student_management;

CREATE TABLE IF NOT EXISTS notification (
    id          BIGINT PRIMARY KEY AUTO_INCREMENT,
    type        VARCHAR(20)  NOT NULL COMMENT 'MANUAL | GRADE_PUBLISH | COURSE_CHANGE | ENROLL',
    title       VARCHAR(100) NOT NULL,
    content     VARCHAR(2000) NOT NULL,
    sender_type VARCHAR(10)  NOT NULL COMMENT 'ADMIN | TEACHER | SYSTEM',
    sender_id   BIGINT       NULL,
    sender_name VARCHAR(50)  NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notification_created (created_at)
) COMMENT '站内通知主表';

CREATE TABLE IF NOT EXISTS notification_receiver (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    notification_id BIGINT  NOT NULL,
    student_id      BIGINT  NOT NULL,
    is_read         TINYINT NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    read_at         DATETIME NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    KEY idx_receiver_student (student_id, is_read),
    KEY idx_receiver_notification (notification_id)
) COMMENT '通知接收明细';
