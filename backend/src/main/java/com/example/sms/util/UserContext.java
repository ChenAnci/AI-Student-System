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

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    // 便捷取值：未登录（未设置）时返回 null，调用方自行判空，避免空指针
    public static Long getUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String getRole() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getRoleType();
    }

    /** 清理当前线程的用户上下文：请求结束必须调用，防止 ThreadLocal 泄漏与跨请求串号 */
    public static void clear() {
        HOLDER.remove();
    }

    @Data
    public static class CurrentUser {
        private Long userId;
        private String userNo;
        private String realName;
        private String roleType;
    }
}
