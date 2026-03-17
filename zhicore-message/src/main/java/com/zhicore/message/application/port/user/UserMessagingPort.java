package com.zhicore.message.application.port.user;

import com.zhicore.api.dto.user.UserSimpleDTO;

/**
 * 用户消息能力端口。
 *
 * 对 application 层暴露最小用户查询能力，
 * 屏蔽底层 Feign 或 RPC 客户端细节。
 */
public interface UserMessagingPort {

    UserSimpleDTO getUserSimple(Long userId);

    boolean isBlocked(Long userId, Long targetUserId);

    boolean isFollowing(Long userId, Long targetUserId);

    boolean isStrangerMessageAllowed(Long userId);
}
