package com.example.sms.util;

import lombok.Data;

/**
 * 当前登录用户上下文（ThreadLocal）
 */
public class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    public static void set(CurrentUser user) {
        HOLDER.set(user);
    }

    public static CurrentUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String getRole() {
        CurrentUser user = HOLDER.get();
        return user == null ? null : user.getRoleType();
    }

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
