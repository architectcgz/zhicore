package com.zhicore.user.application.command;

/**
 * 登录命令。
 */
public record LoginCommand(
        String email,
        String password
) {
}
