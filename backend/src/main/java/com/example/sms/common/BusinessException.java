package com.example.sms.common;

/**
 * 业务异常。
 * code 语义：400=参数/业务错误（HTTP 200 + body.code=400 返回）；
 * 403=权限拒绝（全局处理器会映射为真实 HTTP 403，与拦截器行为一致）；
 * 429=限流（同样走 HTTP 200 + body.code 或拦截器直接写 429，视触发位置而定）。
 * 默认构造按 400 处理，即"业务上不合法的请求"。
 */
public class BusinessException extends RuntimeException {

    /** 业务错误码：400=参数/业务错误；403=权限拒绝；429=限流 */
    private final Integer code;

    /**
     * 默认业务错误：code=400（参数/业务校验不通过）
     *
     * 调用逻辑：Service 层校验失败时抛出（如"学号已存在""所选课程不存在"），
     * 异常沿调用栈传到 GlobalExceptionHandler.handleBusiness，被转换为 {code,message} JSON 返回前端。
     * 为什么用业务异常而非裸 RuntimeException：
     * 1) 携带业务 code，能表达 400（参数/业务错）、403（无权限）、429（限流）等语义，与 500 系统异常区分，
     *    前端据此决定提示文案还是跳登录页，运维也能区分"业务报错"与"系统故障"；
     * 2) 抛出点无需关心如何序列化，由全局处理器统一转 JSON，业务代码更干净。
     */
    public BusinessException(String message) {
        this(400, message);
    }

    /**
     * 指定错误码与提示信息构造业务异常
     *
     * 调用逻辑：需要表达非 400 语义时使用（如权限校验失败抛 new BusinessException(403, "无权限")），
     * 同样由 GlobalExceptionHandler 捕获；code=403 时处理器会映射为真实 HTTP 403。
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /** 供全局异常处理器读取，决定返回 HTTP 200 还是 HTTP 403 */
    public Integer getCode() {
        return code;
    }
}
