package com.zhicore.user.application.command;

/**
 * 更新资料命令。
 */
public record UpdateProfileCommand(
        String nickName,
        String avatarId,
        String bio
) {
}
