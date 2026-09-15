-- ============================================================
-- 学生信息管理系统 数据库初始化脚本
-- MySQL 8.0 | 库名: student_management
-- 账号初始密码统一为 123456 (bcrypt, cost=10)
-- ============================================================

CREATE DATABASE IF NOT EXISTS student_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE student_management;

-- ------------------------------------------------------------
-- 1. 教职工表 staff（教秘 ADMIN / 教师 TEACHER）
-- ------------------------------------------------------------
DROP TABLE IF EXISTS staff;
CREATE TABLE staff (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    staff_no VARCHAR(20) NOT NULL UNIQUE COMMENT '工号（登录账号）',
    password_hash VARCHAR(255) NOT NULL COMMENT 'bcrypt加密密码',
    real_name VARCHAR(50) NOT NULL COMMENT '姓名',
    role_type VARCHAR(20) NOT NULL COMMENT 'ADMIN | TEACHER',
    status VARCHAR(20) DEFAULT 'ENABLED' COMMENT 'ENABLED | FROZEN',
    department VARCHAR(50) COMMENT '所属院系',
    phone VARCHAR(20) COMMENT '手机号',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '教职工表';

-- ------------------------------------------------------------
-- 2. 学生表 student
-- ------------------------------------------------------------
DROP TABLE IF EXISTS student;
CREATE TABLE student (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_no VARCHAR(20) NOT NULL UNIQUE COMMENT '学号（登录账号）',
    password_hash VARCHAR(255) NOT NULL COMMENT 'bcrypt加密密码',
    real_name VARCHAR(50) NOT NULL COMMENT '姓名',
    status VARCHAR(20) DEFAULT 'ENABLED' COMMENT 'ENABLED | FROZEN | SUSPENDED',
    gender VARCHAR(10) COMMENT '男/女',
    phone VARCHAR(20) COMMENT '手机号',
    department VARCHAR(50) COMMENT '院系',
    major VARCHAR(50) COMMENT '专业',
    class_name VARCHAR(50) COMMENT '班级',
    enrollment_year INT COMMENT '入学年份',
    total_earned_credits DECIMAL(8,2) DEFAULT 0.00 COMMENT '已修总学分',
    required_credits DECIMAL(8,2) DEFAULT 0.00 COMMENT '毕业要求总学分',
    gpa DECIMAL(3,2) DEFAULT 0.00 COMMENT '累计平均绩点',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT '学生表';

-- ------------------------------------------------------------
-- 3. 课程表 course
-- ------------------------------------------------------------
DROP TABLE IF EXISTS course;
CREATE TABLE course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_code VARCHAR(20) NOT NULL UNIQUE COMMENT '课程编号',
    course_name VARCHAR(100) NOT NULL COMMENT '课程名称',
    credit DECIMAL(5,2) NOT NULL COMMENT '学分',
    hours INT NOT NULL COMMENT '总学时',
    cover_image_url VARCHAR(500) DEFAULT NULL COMMENT '课程封面图URL',
    teacher_id BIGINT NOT NULL COMMENT '授课教师ID（关联staff.id）',
    schedule VARCHAR(200) COMMENT '上课时间',
    location VARCHAR(100) COMMENT '上课地点',
    capacity INT NOT NULL DEFAULT 30 COMMENT '选课容量上限',
    current_enrolled INT DEFAULT 0 COMMENT '当前已选人数',
    status VARCHAR(20) DEFAULT 'UNPUBLISHED' COMMENT 'UNPUBLISHED未发布 | PUBLISHED已发布',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_teacher (teacher_id),
    KEY idx_status (status)
) COMMENT '课程表';

-- ------------------------------------------------------------
-- 4. 学生选课表 student_course
-- ------------------------------------------------------------
DROP TABLE IF EXISTS student_course;
CREATE TABLE student_course (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL COMMENT '关联student.id',
    course_id BIGINT NOT NULL COMMENT '关联course.id',
    score DECIMAL(5,2) DEFAULT NULL COMMENT '总评成绩（NULL表示未录入）',
    mark VARCHAR(20) DEFAULT 'NORMAL' COMMENT 'NORMAL正常 | DEFER缓考 | ABSENT缺考 | CHEAT舞弊',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_student_course (student_id, course_id),
    KEY idx_course_id (course_id)
) COMMENT '学生选课表';

-- ------------------------------------------------------------
-- 5. 课程成绩审核表 course_grade_audit
-- ------------------------------------------------------------
DROP TABLE IF EXISTS course_grade_audit;
CREATE TABLE course_grade_audit (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    course_id BIGINT NOT NULL UNIQUE COMMENT '课程ID',
    teacher_id BIGINT NOT NULL COMMENT '授课教师ID',
    status VARCHAR(20) DEFAULT 'DRAFT' COMMENT 'DRAFT录入中 | SUBMITTED待审核 | APPROVED审核通过 | PUBLISHED已发布',
    submitted_at DATETIME DEFAULT NULL COMMENT '教师提交时间',
    approved_at DATETIME DEFAULT NULL COMMENT '教秘审核通过时间',
    published_at DATETIME DEFAULT NULL COMMENT '发布时间（发布后永久锁定）',
    reject_reason VARCHAR(500) DEFAULT NULL COMMENT '审核不通过原因',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_course_id (course_id),
    KEY idx_status (status)
) COMMENT '课程成绩审核表';

-- ============================================================
-- 初始数据（密码统一 123456）
-- ============================================================

-- 教职工：1 名教秘 + 2 名教师
INSERT INTO staff (staff_no, password_hash, real_name, role_type, status, department, phone) VALUES
('admin',    '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'ADMIN',   'ENABLED', '教务处', '13800000000'),
('T1001',    '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '王建国',   'TEACHER', 'ENABLED', '计算机学院', '13800000001'),
('T1002',    '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李秀兰',   'TEACHER', 'ENABLED', '计算机学院', '13800000002');

-- 学生：3 名
INSERT INTO student (student_no, password_hash, real_name, status, gender, phone, department, major, class_name, enrollment_year, total_earned_credits, required_credits, gpa) VALUES
('S20230001', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张三', 'ENABLED', '男', '13900000001', '计算机学院', '软件工程', '软工2301', 2023, 3.00, 160.00, 3.00),
('S20230002', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李四', 'ENABLED', '女', '13900000002', '计算机学院', '软件工程', '软工2301', 2023, 0.00, 160.00, 0.00),
('S20230003', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '王五', 'SUSPENDED', '男', '13900000003', '计算机学院', '计算机科学', '计科2301', 2023, 0.00, 160.00, 0.00);

-- 课程：2 门已发布（1门满员）、1 门未发布、1 门待审核
INSERT INTO course (course_code, course_name, credit, hours, cover_image_url, teacher_id, schedule, location, capacity, current_enrolled, status) VALUES
('CS101', 'Java程序设计', 3.00, 48, NULL, 2, '周一 1-2节', '教学楼A101', 30, 2, 'PUBLISHED'),
('CS102', '数据库原理',   2.50, 40, NULL, 3, '周三 3-4节', '教学楼B202', 30, 0, 'PUBLISHED'),
('CS103', 'Web前端开发',  3.00, 48, NULL, 2, '周五 5-6节', '实验楼C303', 30, 0, 'UNPUBLISHED');

-- 选课记录：张三选了 Java（成绩已发布 85 分，已计入 3 学分）
INSERT INTO student_course (student_id, course_id, score, mark, created_at) VALUES
(1, 1, 85.00, 'NORMAL', NOW()),
(2, 1, NULL,  'NORMAL', NOW());

-- 成绩审核表：Java 课程已发布
INSERT INTO course_grade_audit (course_id, teacher_id, status, submitted_at, approved_at, published_at) VALUES
(1, 2, 'PUBLISHED', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));
