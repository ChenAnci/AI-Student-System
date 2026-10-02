package com.example.sms.util; // 声明该类所在包：util 包，存放通用工具类

import lombok.Data; // import 区域：引入 lombok 的 @Data，为内部 CurrentUser 自动生成 getter/setter

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 为什么用 ThreadLocal：一次 HTTP 请求在容器线程中串行执行（拦截器→Controller→Service），
 * 用线程私有变量即可在整条调用链共享当前用户，无需把 user 参数层层透传；
 * 每个线程独立副本天然隔离并发请求，互不串扰。
 * 注意：必须由拦截器在 afterCompletion 中调用 clear()，防止线程池复用导致用户信息串号。
 */
public class UserContext { // 用户上下文工具：用 ThreadLocal 在同一请求线程内共享当前登录用户

    // ThreadLocal 存储当前线程的 CurrentUser；线程内可 get，线程间互不可见
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>(); // 静态线程局部变量：每个线程持有独立的用户副本，天然并发安全

    /**
     * 将当前登录用户写入线程上下文
     *
     * 调用逻辑：JwtInterceptor.preHandle 解析 JWT 成功后调用（一次请求只写入一次），
     * 之后同一请求线程内的 Controller / Service 无需再透传参数即可取到当前用户。
     */
    public static void set(CurrentUser user) { // 写入当前用户：由拦截器在认证成功后调用
        HOLDER.set(user); // 把用户对象放入当前线程的 ThreadLocal 中
    }

    /**
     * 获取当前线程的用户上下文（未登录返回 null）
     *
     * 调用逻辑：Service 层做"仅本人数据 / 角色权限"判断时读取（如学生只能查自己的成绩），
     * 以及需要记录操作人（createBy）时读取；登录校验由拦截器完成，Service 层只管取用。
     * 为什么：线程私有副本保证并发请求互不串扰，避免把 user 对象作为参数在整条调用链层层传递。
     */
    public static CurrentUser get() { // 获取当前用户对象：未登录时返回 null
        return HOLDER.get(); // 返回当前线程保存的用户对象
    }

    // 便捷取值：未登录（未设置）时返回 null，调用方自行判空，避免空指针
    public static Long getUserId() { // 便捷方法：直接取当前用户 ID
        CurrentUser user = HOLDER.get(); // 先取出当前用户对象
        return user == null ? null : user.getUserId(); // 未登录返回 null，否则返回用户 ID
    }

    /** 便捷取值：当前用户角色（未登录返回 null） */
    public static String getRole() { // 便捷方法：直接取当前用户角色
        CurrentUser user = HOLDER.get(); // 先取出当前用户对象
        return user == null ? null : user.getRoleType(); // 未登录返回 null，否则返回角色类型
    }

    /**
     * 清理当前线程的用户上下文：请求结束必须调用，防止 ThreadLocal 泄漏与跨请求串号
     *
     * 调用逻辑：JwtInterceptor.afterCompletion 在请求处理结束（无论成功还是抛异常）时统一调用。
     * 为什么：Web 容器线程由线程池复用，若不清理，ThreadLocal 中的旧用户会残留在线程上，
     * 下一个复用到该线程的请求可能读到上一个请求的用户信息（串号），必须 remove 而非置 null。
     */
    public static void clear() { // 清理上下文：请求结束后由拦截器调用
        HOLDER.remove(); // 移除当前线程的 ThreadLocal 值（必须用 remove，置 null 无法清除 ThreadLocal 本身，可能泄漏）
    }

    /** 当前登录用户信息载体（由拦截器从 JWT Claims 中解析并填充） */
    @Data // lombok 注解：为 CurrentUser 自动生成 getter/setter/toString 等
    public static class CurrentUser { // 内部静态类：承载当前登录用户的基本信息
        /** 用户 ID */
        private Long userId; // 用户 ID（数据库主键）
        /** 工号/学号 */
        private String userNo; // 工号/学号，用于展示与业务关联
        /** 姓名 */
        private String realName; // 用户姓名
        /** 角色：ADMIN / TEACHER / STUDENT */
        private String roleType; // 角色类型，Service 层据此做权限判断
    }
}
