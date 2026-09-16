package com.example.sms.common;

/**
 * 业务异常。
 * code 语义：400=参数/业务错误（HTTP 200 + body.code=400 返回）；
 * 403=权限拒绝（全局处理器会映射为真实 HTTP 403，与拦截器行为一致）；
 * 429=限流（同样走 HTTP 200 + body.code 或拦截器直接写 429，视触发位置而定）。
 * 默认构造按 400 处理，即"业务上不合法的请求"。
 */
public class BusinessException extends RuntimeException {

    private final Integer code;

    /** 默认业务错误：code=400（参数/业务校验不通过） */
    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /** 供全局异常处理器读取，决定返回 HTTP 200 还是 HTTP 403 */
    public Integer getCode() {
        return code;
    }
}
