package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 成绩审核请求
 */
@Data
public class AuditDTO {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    /** true 通过 | false 不通过 */
    @NotNull(message = "审核结果不能为空")
    private Boolean approved;

    /** 不通过时的退回原因 */
    private String rejectReason;
}
