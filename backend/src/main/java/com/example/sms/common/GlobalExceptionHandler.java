package com.example.sms.common;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * 全局异常处理
 *
 * 调用逻辑：任何 Controller / Service 抛出未捕获异常都会进入本类对应的 @ExceptionHandler 方法，
 * 将异常统一转换为 Result 结构返回，保证对前端永远输出统一信封而不是 Spring 默认错误页。
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

    /**
     * 参数校验异常（@Valid 校验失败）：取第一条字段错误提示，统一以 400 返回
     *
     * 调用逻辑：Controller 入参标注 @Valid 且字段约束（@NotBlank/@Email 等）不满足时，
     * Spring 在校参阶段抛出 MethodArgumentNotValidException，由本方法兜底转成 code=400。
     * 为什么：统一为 400（参数错误）并取第一条字段提示的中文信息，避免把 Spring 的英文
     * 校验报文直接透出；分类到 400 而非 500，让前端明确是"用户输入问题"而非"系统故障"。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        // 参数校验失败：取第一个字段错误提示返回 code=400（参数错误），
        // 不把 Spring 的英文校验信息直接透出，统一为中文可读提示。
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return Result.error(400, msg);
    }

    /**
     * 兜底异常：生成 traceId 记录服务端日志，对外仅返回通用提示与 traceId（不暴露堆栈）
     *
     * 调用逻辑：任何未被上面具体 @ExceptionHandler 捕获的异常（空指针、DB 异常等）最终落到本方法，
     * 是对前端响应的最后一道防线。
     * 为什么：
     * 1) 对外只返回 code=500 + traceId、不返回堆栈，防止泄露内部实现细节（类名、SQL、文件路径）给攻击者；
     * 2) 通过 MDC 将 traceId 写入日志上下文并记录完整堆栈，日志中该请求的所有打印都带上 traceId，
     *    用户反馈 traceId 即可在服务端日志快速定位到具体异常；
     * 3) 统一按 500 分类，与 400/403 形成清晰的错误码体系，前端与监控各自处理。
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        // 兜底异常：生成 traceId 并在服务端日志中关联记录完整堆栈（L-1），
        // 对外只返回通用提示 code=500 + traceId，便于用户反馈后快速定位，
        // 不把异常详情/堆栈暴露给客户端（防止泄露内部实现细节给攻击者）。
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        try {
            MDC.put("traceId", traceId);
            log.error("系统异常（traceId={}）", traceId, e);
        } finally {
            MDC.remove("traceId");
        }
        return Result.error(500, "系统繁忙，请稍后重试（traceId: " + traceId + "）");
    }
}
