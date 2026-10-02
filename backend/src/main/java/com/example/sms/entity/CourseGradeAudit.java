// 包声明：本类位于 entity（实体）包，存放与数据库表一一映射的实体类
package com.example.sms.entity;

// ---------- import 区域说明 ----------
// 此处导入 MyBatis-Plus 的注解（实体类与数据库表、主键的映射）、
// Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的 LocalDateTime（本地日期时间）类型。
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 课程成绩审核实体，对应数据库表 course_grade_audit：一门课程一条审核记录，跟踪提交→审核→发布全流程
 */
// @Data：Lombok 注解，编译期自动为该类生成 getter、setter、toString、equals、hashCode 方法
@Data
// @TableName("course_grade_audit")：MyBatis-Plus 注解，声明该实体映射数据库表 course_grade_audit（课程成绩审核表）
@TableName("course_grade_audit")
public class CourseGradeAudit {

    // 主键字段：@TableId 声明主键，IdType.AUTO 表示主键值由数据库自增生成
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 课程ID（关联 course.id） */
    // 课程 ID：外键，关联 course 表主键，标明该审核记录属于哪门课程（一门课程一条记录）
    private Long courseId;

    /** 授课教师ID（关联 staff.id） */
    // 授课教师 ID：外键，关联 staff 表主键，记录负责提交成绩的授课教师
    private Long teacherId;

    /** DRAFT录入中 | SUBMITTED待审核 | APPROVED审核通过 | PUBLISHED已发布 */
    // 审核状态枚举：DRAFT（教师录入中）/ SUBMITTED（已提交待管理员审核）/
    // APPROVED（管理员审核通过）/ PUBLISHED（成绩已向学生发布）
    private String status;

    /** 提交审核时间 */
    // 提交审核时间：教师将成绩提交给管理员审核的时刻
    private LocalDateTime submittedAt;

    /** 审核通过时间 */
    // 审核通过时间：管理员审核通过的时刻
    private LocalDateTime approvedAt;

    /** 成绩发布时间 */
    // 成绩发布时间：成绩正式发布、学生可见的时刻
    private LocalDateTime publishedAt;

    /** 审核不通过原因 */
    // 审核不通过原因：管理员驳回成绩时填写的文字说明，仅在驳回场景下非空
    private String rejectReason;

    /** 创建时间 */
    // 创建时间：该审核记录（成绩录入）创建的时刻
    private LocalDateTime createdAt;

    /** 更新时间 */
    // 更新时间：审核记录每次状态变更时刷新的时刻
    private LocalDateTime updatedAt;
}
