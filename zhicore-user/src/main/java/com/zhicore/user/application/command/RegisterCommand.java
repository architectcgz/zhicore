package com.zhicore.user.application.command;

/**
 * 注册命令。
 */
public record RegisterCommand(
        String userName,
        String email,
        String password
) {
}
