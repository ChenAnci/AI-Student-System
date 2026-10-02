// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）、
// JSR-303 参数校验注解 @DecimalMin / @DecimalMax / @NotNull（数值范围与非空校验），
// 以及 JDK 的高精度小数 BigDecimal（用于成绩等精确数值）。
import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 单条成绩录入
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 成绩录入请求（GradeEntryDTO）中一条学生的成绩明细项
public class GradeItemDTO {

    // @NotNull：学生 ID 不允许为空
    @NotNull(message = "学生ID不能为空")
    // 学生 ID：外键，关联 student 表主键，标明该成绩属于哪位学生
    private Long studentId;

    /** 总评成绩 0-100，缺考/缓考/舞弊时传 null 并设置 mark */
    // @DecimalMin：成绩不能小于 0 分
    @DecimalMin(value = "0", message = "成绩不能小于0")
    // @DecimalMax：成绩不能大于 100 分
    @DecimalMax(value = "100", message = "成绩不能大于100")
    // 总评成绩：0~100 分制；缺考/缓考/舞弊等异常情况传 null，并改用 mark 字段标记
    private BigDecimal score;

    /** NORMAL 正常 | DEFER 缓考 | ABSENT 缺考 | CHEAT 舞弊 */
    // 考试标记枚举：NORMAL（正常）/ DEFER（缓考）/ ABSENT（缺考）/ CHEAT（舞弊），
    // 与 score 配合使用：score 为 null 时必须设置该标记
    private String mark;
}
