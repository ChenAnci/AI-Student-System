package com.example.sms.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 学生学业仪表盘 VO
 */
@Data
public class DashboardVO {

    private BigDecimal totalEarnedCredits;
    private BigDecimal requiredCredits;
    /** 完成进度百分比 0-100 */
    private BigDecimal progressPercent;
    private BigDecimal gpa;
    /** 各课程成绩明细 */
    private List<GradeVO> gradeList;
}
