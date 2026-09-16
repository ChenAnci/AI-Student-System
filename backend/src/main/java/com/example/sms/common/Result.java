package com.example.sms.common;

import lombok.Data;

/**
 * 统一响应结果。
 * code 约定：200=成功；400=参数/业务错误；401=未登录；403=无权限（配合 HTTP 403）；
 * 429=限流；500=系统异常。前端依据 code 分支处理，message 仅作展示用。
 */
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.setCode(200);
        r.setMessage("操作成功");
        r.setData(data);
        return r;
    }

    /** 便捷构造错误结果：未指定 code 时按 500（系统异常）处理 */
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
