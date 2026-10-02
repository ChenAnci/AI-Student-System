package com.example.sms.common; // 声明该类所在包：common 包，存放通用组件

import lombok.Data; // import 区域：引入 lombok 的 @Data，自动生成 getter/setter/toString 等

/**
 * 统一响应结果。
 * code 约定：200=成功；400=参数/业务错误；401=未登录；403=无权限（配合 HTTP 403）；
 * 429=限流；500=系统异常。前端依据 code 分支处理，message 仅作展示用。
 * 为什么统一 {code,message,data} 信封：所有 Controller 统一返回本结构，前端 http.ts 拦截器
 * 只需判 code 即可统一走"成功回调 / 错误提示"分支（错误提示文案统一化，无需每处接口各自处理），
 * 也便于网关、监控按 code 统计错误类型；若各接口各自返回不同结构，前端与调用方都要为每种结构写适配。
 */
@Data // lombok 注解：为 Result 自动生成所有字段的 getter/setter、equals/hashCode/toString
public class Result<T> { // 泛型统一响应信封：T 为业务数据类型，成功时放入 data

    /** 状态码：200=成功；400=参数/业务错误；401=未登录；403=无权限；429=限流；500=系统异常 */
    private Integer code; // 状态码字段：前端拦截器据此判断成功/失败，进入不同分支
    /** 提示信息（仅作前端展示用） */
    private String message; // 提示信息字段：仅用于前端 toast 展示，不作为逻辑判断依据
    /** 业务数据（成功时携带，错误时通常为 null） */
    private T data; // 业务数据字段：成功响应时装载查询/操作结果，错误响应时为 null

    /**
     * 成功响应（不带数据体）
     *
     * 调用逻辑：仅需返回成功状态、无需携带业务数据时使用（如删除成功）；
     * 内部委托 success(null)，最终响应 data 为 null。
     */
    public static <T> Result<T> success() { // 无参成功构造：适合"删除/禁用成功"等无需返回数据体的场景
        return success(null); // 委托带参 success(null)，data 置空表示无业务数据返回
    }

    /**
     * 成功响应（携带业务数据）
     *
     * 调用逻辑：Controller 在业务处理成功后统一返回（如查询列表/详情、新增/修改成功），
     * 由 Spring 自动序列化为 {code:200, message, data} 返回前端；success() 无参重载内部委托本方法。
     * 为什么：data 泛型化，查询/分页/详情等不同数据体共用同一信封，前端统一从 res.data 取业务数据。
     */
    public static <T> Result<T> success(T data) { // 带数据成功构造：查询/详情等接口返回业务数据时使用
        Result<T> r = new Result<>(); // 新建一个空的响应对象
        r.setCode(200); // 置状态码 200：表示请求成功
        r.setMessage("操作成功"); // 置默认成功提示文案（前端仅展示用）
        r.setData(data); // 装载业务数据（如列表、详情对象）
        return r; // 返回组装完成的成功信封
    }

    /**
     * 便捷构造错误结果：未指定 code 时按 500（系统异常）处理
     *
     * 调用逻辑：调用方只关心"报错并提示"、不细分错误码时使用；
     * 内部委托 error(500, message)，与兜底异常处理（code=500）口径一致。
     */
    public static <T> Result<T> error(String message) { // 无 code 错误构造：不细分错误类型时默认按 500 处理
        return error(500, message); // 委托带 code 的 error(500, message)，与系统异常口径一致
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
    public static <T> Result<T> error(Integer code, String message) { // 完整错误构造：可指定错误码与提示文案
        Result<T> r = new Result<>(); // 新建一个空的响应对象
        r.setCode(code); // 置业务错误码（如 400/401/403/500）
        r.setMessage(message); // 置错误提示文案（前端 toast 展示）
        return r; // 返回组装完成的错误信封（data 保持 null）
    }
}
