package com.example.sms.common;

import lombok.Data;

/**
 * 统一响应结果。
 * code 约定：200=成功；400=参数/业务错误；401=未登录；403=无权限（配合 HTTP 403）；
 * 429=限流；500=系统异常。前端依据 code 分支处理，message 仅作展示用。
 * 为什么统一 {code,message,data} 信封：所有 Controller 统一返回本结构，前端 http.ts 拦截器
 * 只需判 code 即可统一走"成功回调 / 错误提示"分支（错误提示文案统一化，无需每处接口各自处理），
 * 也便于网关、监控按 code 统计错误类型；若各接口各自返回不同结构，前端与调用方都要为每种结构写适配。
 */
@Data
public class Result<T> {

    /** 状态码：200=成功；400=参数/业务错误；401=未登录；403=无权限；429=限流；500=系统异常 */
    private Integer code;
    /** 提示信息（仅作前端展示用） */
    private String message;
    /** 业务数据（成功时携带，错误时通常为 null） */
    private T data;

    /**
     * 成功响应（不带数据体）
     *
     * 调用逻辑：仅需返回成功状态、无需携带业务数据时使用（如删除成功）；
     * 内部委托 success(null)，最终响应 data 为 null。
     */
    public static <T> Result<T> success() {
        return success(null);
    }

    /**
     * 成功响应（携带业务数据）
     *
     * 调用逻辑：Controller 在业务处理成功后统一返回（如查询列表/详情、新增/修改成功），
     * 由 Spring 自动序列化为 {code:200, message, data} 返回前端；success() 无参重载内部委托本方法。
     * 为什么：data 泛型化，查询/分页/详情等不同数据体共用同一信封，前端统一从 res.data 取业务数据。
     */
    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("操作成功");
        r.setData(data);
        return r;
    }

    /**
     * 便捷构造错误结果：未指定 code 时按 500（系统异常）处理
     *
     * 调用逻辑：调用方只关心"报错并提示"、不细分错误码时使用；
     * 内部委托 error(500, message)，与兜底异常处理（code=500）口径一致。
     */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    /**
     * 便捷构造错误结果：指定 code 与提示信息，data 留空
     *
     * 调用逻辑：Controller 主动返回业务错误时使用（如"数据不存在""操作不允许"），
     * 以及 GlobalExceptionHandler 将异常转换为统一错误响应时使用；
     * error(String) 无 code 重载内部委托本方法（默认 500）。
     * 为什么：错误同样走统一信封，前端拦截器看到 code != 200 直接提示 message，
     * 无需区分错误来源是异常还是业务判断，前端处理逻辑单一。
     */
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
