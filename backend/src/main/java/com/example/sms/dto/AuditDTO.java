// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JSR-303 参数校验注解 @NotNull（用于非空校验）。
import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 成绩审核请求
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 管理员对教师提交的成绩进行审核（通过/驳回）时，前端提交的请求体
public class AuditDTO {

    // @NotNull：课程 ID 不允许为空
    @NotNull(message = "课程ID不能为空")
    // 课程 ID：待审核成绩所属课程的 id，定位是哪门课程的审核记录
    private Long courseId;

    /** true 通过 | false 不通过 */
    // @NotNull：审核结果不允许为空
    @NotNull(message = "审核结果不能为空")
    // 审核结果：true 表示审核通过，false 表示不通过（驳回给教师修改）
    private Boolean approved;

    /** 不通过时的退回原因 */
    // 退回原因：当 approved=false（不通过）时填写的驳回说明，便于教师知晓修改方向
    private String rejectReason;
}
