package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 教学秘书添加账号请求
 */
@Data
public class AccountCreateDTO {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    /** TEACHER | STUDENT */
    @NotBlank(message = "角色不能为空")
    private String roleType;

    /** 学生专属字段 */
    private String gender;
    private String department;
    private String major;
    private String className;
    private Integer enrollmentYear;
    private String phone;
}
