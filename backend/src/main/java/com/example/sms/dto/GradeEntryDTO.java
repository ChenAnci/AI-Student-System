package com.example.sms.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 成绩录入请求（单个或多个学生）
 */
@Data
public class GradeEntryDTO {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @Valid
    @NotEmpty(message = "成绩列表不能为空")
    private List<GradeItemDTO> items;
}
