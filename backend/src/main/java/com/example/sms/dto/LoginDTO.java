// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JSR-303 参数校验注解 @NotBlank（用于非空校验）。
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 用户登录时前端提交的请求体（学生用学号、教师/管理员用工号作为账号）
public class LoginDTO {

    // @NotBlank：账号不允许为空
    @NotBlank(message = "账号不能为空")
    // 账号：学号（学生）或工号（教师/管理员），登录标识
    private String username;

    // @NotBlank：密码不允许为空
    @NotBlank(message = "密码不能为空")
    // 密码：用户输入的明文密码，服务端会与 bcrypt 加密哈希比对
    private String password;
}
