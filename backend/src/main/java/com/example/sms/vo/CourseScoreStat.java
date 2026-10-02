// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JDK 的高精度小数 BigDecimal（用于成绩等精确数值）。
import lombok.Data;

import java.math.BigDecimal;

/**
 * 教师课程成绩统计项
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 教师端成绩统计列表中，某一门课程的成绩统计项
public class CourseScoreStat {

    // 课程名称：被统计的课程标题
    private String courseName;
    /** 平均分 */
    // 平均分：该课程全部已评分学生成绩的平均值
    private BigDecimal avgScore;
    /** 已评分学生数 */
    // 已评分学生数：已录入成绩的学生人数
    private Long studentCount;
    /** 通过率（0-100） */
    // 通过率：及格（>=60 分）学生占比，取值 0~100
    private BigDecimal passRate;
}
