package com.example.sms.controller;

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
@Api(tags = "登录认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @ApiOperation("登录（工号/学号 + 密码）")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(authService.login(dto));
    }
}
