package com.zhicore.common.context;

import com.zhicore.common.exception.UnauthorizedException;
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
     * 是否为管理员。
     *
     * <p>约定：UserInfo.role 为 "admin" / "ADMIN" 时视为管理员，其余均为非管理员。
     * role 缺失时默认非管理员。
     */
    public static boolean isAdmin() {
        UserInfo userInfo = USER_HOLDER.get();
        if (userInfo == null || userInfo.getRole() == null) {
            return false;
        }
        return "admin".equalsIgnoreCase(userInfo.getRole());
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
        private String role;

        public UserInfo() {
        }

        public UserInfo(String userId, String userName) {
            this.userId = userId;
            this.userName = userName;
        }
    }

    /**
     * 获取当前用户ID；未登录时抛出未授权异常。
     */
    public static Long requireUserId() {
        Long userId = getUserId();
        if (userId == null) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }
}
