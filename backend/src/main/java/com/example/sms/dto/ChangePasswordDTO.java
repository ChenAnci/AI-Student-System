// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JSR-303 参数校验注解：@NotBlank（非空）、@Pattern（正则）、@Size（长度范围）。
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 自助修改密码请求
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 用户（学生/教师/管理员）登录后自助修改密码时，前端提交的请求体
public class ChangePasswordDTO {

    // @NotBlank：旧密码不允许为空
    @NotBlank(message = "旧密码不能为空")
    // 旧密码：用户当前使用的密码，服务端会先校验其是否正确（需与数据库 bcrypt 哈希匹配）
    private String oldPassword;

    /** 新密码：8~20 位，需同时包含字母与数字（强度校验，避免弱口令） */
    // @NotBlank：新密码不允许为空
    @NotBlank(message = "新密码不能为空")
    // @Size：新密码长度必须在 8~20 位之间，防止过短弱口令或过长输入
    @Size(min = 8, max = 20, message = "新密码长度需为 8~20 位")
    // @Pattern：正则校验，新密码必须同时包含至少一个字母和一个数字，提升密码强度
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码需同时包含字母和数字")
    // 新密码：用户设置的新密码，服务端会使用 bcrypt 加密后存储
    private String newPassword;
}
