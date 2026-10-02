// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的高精度小数 BigDecimal（用于成绩/绩点等精确数值）。
import lombok.Data;

import java.math.BigDecimal;

/**
 * 学生成绩单 VO
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 学生成绩单中单门课程的成绩项：学生端"我的成绩"页面展示
public class GradeVO {

    // 课程 ID：外键，关联 course 表主键
    private Long courseId;
    // 课程名称：课程标题
    private String courseName;
    // 学分：该课程学分值（BigDecimal 保证精度）
    private BigDecimal credit;
    // 总评成绩：0~100 分制，NULL 表示未出成绩
    private BigDecimal score;
    /** NORMAL | DEFER | ABSENT | CHEAT */
    // 考试标记：NORMAL（正常）/ DEFER（缓考）/ ABSENT（缺考）/ CHEAT（舞弊）
    private String mark;
    /** DRAFT | SUBMITTED | APPROVED | PUBLISHED */
    // 成绩审核状态：DRAFT（录入中）/ SUBMITTED（待审核）/ APPROVED（已通过）/ PUBLISHED（已发布）
    private String auditStatus;
    /** 是否通过（已发布且 score >= 60） */
    // 是否通过：成绩已发布（PUBLISHED）且分数 >= 60 时为 true
    private Boolean passed;
    /** 单科绩点：已发布且有成绩时 = toGradePoint(score)，未发布/无成绩为 null */
    // 单科绩点：成绩已发布且有分数时由 toGradePoint(score) 换算；未发布或无成绩时为 null
    private BigDecimal gradePoint;
}
