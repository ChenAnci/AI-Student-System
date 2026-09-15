package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 课程创建/编辑请求
 */
@Data
public class CourseFormDTO {

    @NotBlank(message = "课程编号不能为空")
    private String courseCode;

    @NotBlank(message = "课程名称不能为空")
    private String courseName;

    @NotNull(message = "学分不能为空")
    private BigDecimal credit;

    @NotNull(message = "学时不能为空")
    private Integer hours;

    private String coverImageUrl;
    private String schedule;
    private String location;

    /** 授课教师 ID（可选）：教学秘书创建/编辑课程时可指定，教师本人创建时忽略 */
    private Long teacherId;

    @NotNull(message = "容量不能为空")
    private Integer capacity;
}
