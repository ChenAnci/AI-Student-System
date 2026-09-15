package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 账号信息编辑请求（学号/工号与角色不可修改）
 */
@Data
public class AccountUpdateDTO {

    @NotBlank(message = "姓名不能为空")
    private String realName;

    /** 学生：性别 */
    private String gender;

    private String phone;
    private String department;

    /** 学生：专业 / 班级 / 入学年份 */
    private String major;
    private String className;
    private Integer enrollmentYear;
}
