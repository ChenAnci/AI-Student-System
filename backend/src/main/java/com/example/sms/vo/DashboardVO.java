// 包声明：本类位于 vo（视图对象）包，用于承载服务端返回给前端的展示数据
package com.example.sms.vo;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）、
// JDK 的高精度小数 BigDecimal（用于学分/绩点等精确数值），
// 以及 List（列表类型，承载成绩明细列表）。
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生学业仪表盘 VO
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 学生端"我的学业"仪表盘接口返回的数据对象
public class DashboardVO {

    // 已修总学分：学生目前已获得并通过考核的学分累计
    private BigDecimal totalEarnedCredits;
    // 毕业要求总学分：学生毕业需达到的学分要求
    private BigDecimal requiredCredits;
    /** 完成进度百分比 0-100 */
    // 完成进度百分比：已修学分 / 要求学分，取值 0~100，用于前端进度条展示
    private BigDecimal progressPercent;
    // 累计平均绩点 GPA：衡量学业水平的综合指标
    private BigDecimal gpa;
    /** 各课程成绩明细 */
    // 各课程成绩明细：该学生全部课程的成绩列表
    private List<GradeVO> gradeList;
}
