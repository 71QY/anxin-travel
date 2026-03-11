package com.anxin.travel.common.util;

public class UserContext {
    private static final ThreadLocal<Long> currentUser = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        currentUser.set(userId);
    }

    public static Long getUserId() {
        return currentUser.get();
    }

    public static void clear() {
        currentUser.remove();
    }
}