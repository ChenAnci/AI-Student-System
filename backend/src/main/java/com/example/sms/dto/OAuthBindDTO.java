// 包声明：本类位于 dto（数据传输对象）包，用于接收前端提交的请求参数并做参数校验
package com.example.sms.dto;

// ---------- import 区域说明 ----------
// 此处导入 Lombok 的 @Data（自动生成 getter/setter 等样板代码），
// 以及 JSR-303 参数校验注解 @NotBlank（用于非空校验）。
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * OAuth 绑定现有账号请求
 */
// @Data：Lombok 注解，编译期自动生成 getter、setter、toString、equals、hashCode 方法
@Data
// 首次使用 GitHub 登录时，如果 GitHub 账号未绑定系统账号，
// 前端引导用户输入系统账号密码进行绑定时提交的请求体
public class OAuthBindDTO {

    // @NotBlank：账号不允许为空
    @NotBlank(message = "账号不能为空")
    // 账号：学号（学生）或工号（教师/管理员），用于定位要绑定的系统账号
    private String username;

    // @NotBlank：密码不允许为空
    @NotBlank(message = "密码不能为空")
    // 密码：该系统账号的密码，用于校验绑定操作确为账号本人发起
    private String password;

    /** GitHub 唯一用户 id（来自 OAuth 回调，用于与现有账号绑定） */
    // @NotBlank：GitHub 用户 ID 不允许为空
    @NotBlank(message = "绑定凭证缺失")
    // GitHub 唯一用户 id：来自 OAuth 回调流程，用于建立 GitHub 账号与系统账号的绑定关系
    private String providerUid;
}
