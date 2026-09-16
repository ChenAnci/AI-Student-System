package com.example.sms.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常：code=403 的权限拒绝返回真实 HTTP 403（与拦截器行为一致，便于网关统计），其余保持 HTTP 200 + body.code。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        // 403（权限拒绝/未授权）映射为真实 HTTP 403：让网关/WAF/监控能按状态码识别拦截行为；
        // 其余业务错误（400 参数错、429 限流等）保持 HTTP 200 + body.code 返回，
        // 由前端按 code 统一处理，避免浏览器对 4xx/5xx 的默认行为干扰前端交互。
        HttpStatus status = e.getCode() == 403 ? HttpStatus.FORBIDDEN : HttpStatus.OK;
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        // 参数校验失败：取第一个字段错误提示返回 code=400（参数错误），
        // 不把 Spring 的英文校验信息直接透出，统一为中文可读提示。
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(400, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        // 兜底异常：记录完整堆栈便于排查，但对外只返回通用提示 code=500，
        // 不把异常详情/堆栈暴露给客户端（防止泄露内部实现细节给攻击者）。
        log.error("系统异常", e);
        return Result.error(500, "系统繁忙，请稍后重试");
    }
}
