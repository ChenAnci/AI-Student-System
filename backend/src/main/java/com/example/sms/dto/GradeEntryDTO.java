// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码）、
// JSR-303 参数校验注解 @Valid（级联校验）/ @NotEmpty / @NotNull，
// 以及 JDK 的 List（列表类型，承载多条成绩明细）。
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 成绩录入请求（单个或多个学生）
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 教师录入课程成绩时，前端提交的请求体，支持一次提交多个学生的成绩
public class GradeEntryDTO {

    // @NotNull：课程 ID 不允许为空
    @NotNull(message = "课程ID不能为空")
    // 课程 ID：本次录入成绩所属课程的 id
    private Long courseId;

    // @Valid：对列表中的每个元素执行其内部定义的校验规则（级联校验 GradeItemDTO）
    @Valid
    // @NotEmpty：成绩明细列表不允许为空
    @NotEmpty(message = "成绩列表不能为空")
    // 成绩明细列表：包含多个学生的成绩项（每项含学生 ID、成绩、考试标记）
    private List<GradeItemDTO> items;
}
