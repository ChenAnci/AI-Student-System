// 声明包名：本类属于 controller（控制器）包，统一负责接收 HTTP 请求并返回响应
package com.example.sms.controller;

// ===== 导入依赖：统一响应体 Result、登录相关 DTO、认证服务接口、Swagger 注解、Spring MVC 注解与参数校验 =====
import com.example.sms.common.Result;
import com.example.sms.dto.LoginDTO;
import com.example.sms.dto.LoginResponse;
import com.example.sms.service.AuthService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * 登录认证接口
 */
// Swagger 注解：把本控制器在文档中归入"登录认证"分组
@Api(tags = "登录认证")
// Spring MVC 注解：标记为 REST 控制器，方法返回值自动以 JSON 格式写回响应体
@RestController
// 类级路由前缀：本控制器所有接口统一以 /api/auth 开头
@RequestMapping("/api/auth")
// 登录认证控制器：只负责接收登录请求与结果封装，认证逻辑委托给 AuthService
public class AuthController {

    // 自动注入认证服务：由 Spring 容器装配 AuthService 的实现类
    @Autowired
    private AuthService authService;

    /** 登录：按工号/学号 + 密码校验，成功返回用户信息与 token（@Valid 先校验入参非空/格式） */
    // Swagger 接口说明：登录（工号/学号 + 密码）
    @ApiOperation("登录（工号/学号 + 密码）")
    // POST 映射：访问 /api/auth/login 发起登录
    @PostMapping("/login")
    // 处理登录请求：@Valid 先做入参非空/格式校验，@RequestBody 将 JSON 请求体绑定为 LoginDTO
    public Result<LoginResponse> login(@Valid @RequestBody LoginDTO dto) {
        // 调用认证服务完成账号密码校验，成功则返回用户信息与 token，并封装为成功响应
        return Result.success(authService.login(dto));
    }
}
