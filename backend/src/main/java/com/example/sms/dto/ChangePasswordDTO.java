package com.example.sms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 自助修改密码请求
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /** 新密码：8~20 位，需同时包含字母与数字（强度校验，避免弱口令） */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 20, message = "新密码长度需为 8~20 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码需同时包含字母和数字")
    private String newPassword;
}
