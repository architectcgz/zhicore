package com.zhicore.user.application.command;

/**
 * 刷新令牌命令。
 */
public record RefreshTokenCommand(
        String refreshToken
) {
}
