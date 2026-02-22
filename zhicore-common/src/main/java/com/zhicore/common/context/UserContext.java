package com.zhicore.common.context;

import lombok.Data;

/**
 * 用户上下文
 * 用于在请求链路中传递用户信息
 */
public class UserContext {

    private static final ThreadLocal<UserInfo> USER_HOLDER = new ThreadLocal<>();

    private UserContext() {
    }

    /**
     * 设置当前用户
     */
    public static void setUser(UserInfo userInfo) {
        USER_HOLDER.set(userInfo);
    }

    /**
     * 获取当前用户
     */
    public static UserInfo getUser() {
        return USER_HOLDER.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        UserInfo userInfo = USER_HOLDER.get();
        if (userInfo == null || userInfo.getUserId() == null) {
            return null;
        }
        try {
            return Long.parseLong(userInfo.getUserId());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 获取当前用户名
     */
    public static String getUserName() {
        UserInfo userInfo = USER_HOLDER.get();
        return userInfo != null ? userInfo.getUserName() : null;
    }

    /**
     * 清除当前用户
     */
    public static void clear() {
        USER_HOLDER.remove();
    }

    /**
     * 用户信息
     */
    @Data
    public static class UserInfo {
        private String userId;
        private String userName;
        private String email;

        public UserInfo() {
        }

        public UserInfo(String userId, String userName) {
            this.userId = userId;
            this.userName = userName;
        }
    }
}
