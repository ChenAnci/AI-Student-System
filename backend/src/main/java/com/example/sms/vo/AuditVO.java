// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的 LocalDateTime（本地日期时间）类型。
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 待审核/成绩流程 VO（教秘审核、教师提交后查看）
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 成绩审核流程列表项：教学秘书（管理员）审核成绩、教师查看提交结果时展示的数据对象
public class AuditVO {

    // 课程 ID：外键，关联 course 表主键
    private Long courseId;
    // 课程编号：教务系统分配的唯一课程编码
    private String courseCode;
    // 课程名称：课程标题
    private String courseName;
    // 授课教师姓名：负责该课程成绩录入与提交的教师
    private String teacherName;
    /** 成绩审核流程状态：DRAFT 草稿 | SUBMITTED 待审核 | APPROVED 已通过 | PUBLISHED 已发布 */
    // 成绩审核流程状态：DRAFT（草稿，录入中）/ SUBMITTED（已提交待审核）/
    // APPROVED（审核通过）/ PUBLISHED（已发布给学生）
    private String status;
    // 提交审核时间：教师将成绩提交给管理员审核的时刻
    private LocalDateTime submittedAt;
    // 审核通过时间：管理员审核通过的时刻
    private LocalDateTime approvedAt;
    // 成绩发布时间：成绩正式发布、学生可见的时刻
    private LocalDateTime publishedAt;
    // 审核不通过原因：被驳回时的文字说明
    private String rejectReason;
}
