package com.example.sms.common; // 声明该类所在包：common 包，存放全局通用组件

// ---- import 区域：引入 lombok 日志、MDC 日志上下文、Spring 的异常处理注解/校验异常类、UUID 等依赖 ----
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
@Slf4j // lombok 注解：自动生成名为 log 的日志对象，供本类打印异常堆栈
@RestControllerAdvice // 全局异常通知：拦截所有 Controller 抛出的异常，按异常类型分发到下方处理方法
public class GlobalExceptionHandler { // 全局异常处理器：把各类异常统一转为 Result 统一响应

    /**
     * 业务异常：code=403 的权限拒绝返回真实 HTTP 403（与拦截器行为一致，便于网关统计），其余保持 HTTP 200 + body.code。
     */
    @ExceptionHandler(BusinessException.class) // 声明本方法专门处理 BusinessException 业务异常
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) { // 返回 ResponseEntity 以携带自定义 HTTP 状态码
        // 403（权限拒绝/未授权）映射为真实 HTTP 403：让网关/WAF/监控能按状态码识别拦截行为；
        // 其余业务错误（400 参数错、429 限流等）保持 HTTP 200 + body.code 返回，
        // 由前端按 code 统一处理，避免浏览器对 4xx/5xx 的默认行为干扰前端交互。
        HttpStatus status = e.getCode() == 403 ? HttpStatus.FORBIDDEN : HttpStatus.OK; // 根据业务码选择真实 HTTP 状态：403→403，其余→200
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage())); // 组装带状态码的响应体，body 为统一错误信封
    }

    /**
     * 参数校验异常（@Valid 校验失败）：取第一条字段错误提示，统一以 400 返回
     *
     * 调用逻辑：Controller 入参标注 @Valid 且字段约束（@NotBlank/@Email 等）不满足时，
     * Spring 在校参阶段抛出 MethodArgumentNotValidException，由本方法兜底转成 code=400。
     * 为什么：统一为 400（参数错误）并取第一条字段提示的中文信息，避免把 Spring 的英文
     * 校验报文直接透出；分类到 400 而非 500，让前端明确是"用户输入问题"而非"系统故障"。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class) // 声明专门处理 @Valid 参数校验失败的异常
    public Result<Void> handleValid(MethodArgumentNotValidException e) { // 处理校验异常：返回标准 Result（HTTP 200 + body.code=400）
        // 参数校验失败：取第一个字段错误提示返回 code=400（参数错误），
        // 不把 Spring 的英文校验信息直接透出，统一为中文可读提示。
        FieldError fieldError = e.getBindingResult().getFieldError(); // 从校验结果中取第一个字段错误对象
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败"; // 有字段错误就取其中文提示，否则用兜底文案
        return Result.error(400, msg); // 统一按"参数错误"code=400 返回给前端
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
    @ExceptionHandler(Exception.class) // 声明兜底处理所有未被上面具体处理器捕获的异常
    public Result<Void> handleOther(Exception e) { // 兜底异常处理：对任何未预期异常的最后一道防线
        // 兜底异常：生成 traceId 并在服务端日志中关联记录完整堆栈（L-1），
        // 对外只返回通用提示 code=500 + traceId，便于用户反馈后快速定位，
        // 不把异常详情/堆栈暴露给客户端（防止泄露内部实现细节给攻击者）。
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16); // 生成 16 位随机 traceId，用于在日志中串联定位一次异常
        try { // try 块：先写入 MDC 再打日志，保证日志中带 traceId
            MDC.put("traceId", traceId); // 把 traceId 写入日志上下文：本次请求后续所有日志输出都会自动带上该值
            log.error("系统异常（traceId={}）", traceId, e); // 在服务端打印完整异常堆栈并关联 traceId（e 作为最后一个参数输出堆栈）
        } finally { // finally 块：无论成功失败都要清理 MDC，防止线程池复用时 traceId 串到下一个请求
            MDC.remove("traceId"); // 从日志上下文移除 traceId，避免残留污染其他请求的日志
        }
        return Result.error(500, "系统繁忙，请稍后重试（traceId: " + traceId + "）"); // 对外只返回 code=500 + traceId，不暴露任何内部细节
    }
}
