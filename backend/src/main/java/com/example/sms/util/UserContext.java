package com.example.sms.util;

import lombok.Data;

/**
 * 当前登录用户上下文（ThreadLocal）。
 * 为什么用 ThreadLocal：一次 HTTP 请求在容器线程中串行执行（拦截器→Controller→Service），
 * 用线程私有变量即可在整条调用链共享当前用户，无需把 user 参数层层透传；
 * 每个线程独立副本天然隔离并发请求，互不串扰。
 * 注意：必须由拦截器在 afterCompletion 中调用 clear()，防止线程池复用导致用户信息串号。
 */
public class UserContext {

    // ThreadLocal 存储当前线程的 CurrentUser；线程内可 get，线程间互不可见
    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    /**
     * 将当前登录用户写入线程上下文
     *
     * 调用逻辑：JwtInterceptor.preHandle 解析 JWT 成功后调用（一次请求只写入一次），
     * 之后同一请求线程内的 Controller / Service 无需再透传参数即可取到当前用户。
     */
    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    /**
     * 获取当前线程的用户上下文（未登录返回 null）
     *
     * 调用逻辑：Service 层做"仅本人数据 / 角色权限"判断时读取（如学生只能查自己的成绩），
     * 以及需要记录操作人（createBy）时读取；登录校验由拦截器完成，Service 层只管取用。
     * 为什么：线程私有副本保证并发请求互不串扰，避免把 user 对象作为参数在整条调用链层层传递。
     */
    public static CurrentUser get() {
        return HOLDER.get();
    }

    // 便捷取值：未登录（未设置）时返回 null，调用方自行判空，避免空指针
    public static Long getUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    /** 便捷取值：当前用户角色（未登录返回 null） */
    public static String getRole() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getRoleType();
    }

    /**
     * 清理当前线程的用户上下文：请求结束必须调用，防止 ThreadLocal 泄漏与跨请求串号
     *
     * 调用逻辑：JwtInterceptor.afterCompletion 在请求处理结束（无论成功还是抛异常）时统一调用。
     * 为什么：Web 容器线程由线程池复用，若不清理，ThreadLocal 中的旧用户会残留在线程上，
     * 下一个复用到该线程的请求可能读到上一个请求的用户信息（串号），必须 remove 而非置 null。
     */
    public static void clear() {
        HOLDER.remove();
    }

    /** 当前登录用户信息载体（由拦截器从 JWT Claims 中解析并填充） */
    @Data
    public static class CurrentUser {
        /** 用户 ID */
        private Long userId;
        /** 工号/学号 */
        private String userNo;
        /** 姓名 */
        private String realName;
        /** 角色：ADMIN / TEACHER / STUDENT */
        private String roleType;
    }
}
