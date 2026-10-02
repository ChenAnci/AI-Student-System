// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的高精度小数 BigDecimal（用于学分等精确数值）。
import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师端课程列表 VO（含成绩审核状态）
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 教师端"我的课程"列表项：展示教师所授课程的基本信息与成绩审核状态
public class MyCourseVO {

    // 课程 ID：数据库主键
    private Long id;
    // 课程编号：教务系统分配的唯一课程编码
    private String courseCode;
    // 课程名称：课程标题
    private String courseName;
    // 学分：课程学分值
    private BigDecimal credit;
    // 总学时：课程教学总课时数
    private Integer hours;
    // 上课时间：文字描述排课时间
    private String schedule;
    // 上课地点：文字描述上课教室/地点
    private String location;
    // 选课容量上限：最多可选课人数
    private Integer capacity;
    // 当前已选人数：已选课学生数
    private Integer currentEnrolled;
    /** 课程状态：UNPUBLISHED 未发布 | PUBLISHED 已发布 */
    // 课程状态：UNPUBLISHED（未发布）/ PUBLISHED（已发布）
    private String status;
    // 课程封面图 URL：课程卡片展示的图片地址
    private String coverImageUrl;
    /** 授课教师 ID 与姓名（管理端展示/回填用） */
    // 授课教师 ID：外键，关联 staff 表；管理端展示与编辑回填时使用
    private Long teacherId;
    // 授课教师姓名：冗余展示教师姓名
    private String teacherName;
    /** DRAFT | SUBMITTED | APPROVED | PUBLISHED | null(未发起) */
    // 成绩审核状态：DRAFT（录入中）/ SUBMITTED（待审核）/ APPROVED（已通过）/
    // PUBLISHED（已发布）/ null（从未发起过成绩录入）
    private String auditStatus;
}
