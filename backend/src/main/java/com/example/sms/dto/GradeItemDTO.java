package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 单条成绩录入
 */
@Data
public class GradeItemDTO {

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    /** 总评成绩 0-100，缺考/缓考/舞弊时传 null 并设置 mark */
    @DecimalMin(value = "0", message = "成绩不能小于0")
    @DecimalMax(value = "100", message = "成绩不能大于100")
    private BigDecimal score;

    /** NORMAL 正常 | DEFER 缓考 | ABSENT 缺考 | CHEAT 舞弊 */
    private String mark;
}
