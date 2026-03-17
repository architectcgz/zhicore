package com.zhicore.message.infrastructure.feign;

import com.zhicore.api.client.UserMessagingClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.port.user.UserMessagingPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 用户消息端口的 Feign 适配器。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeignUserMessagingPort implements UserMessagingPort {

    private final UserMessagingClient userMessagingClient;

    @Override
    public UserSimpleDTO getUserSimple(Long userId) {
        ApiResponse<UserSimpleDTO> response = userMessagingClient.getUserSimple(userId);
        if (response == null) {
            throw degraded("getUserSimple", userId, response);
        }
        if (!response.isSuccess()) {
            if (response.getCode() == ResultCode.USER_NOT_FOUND.getCode()
                    || response.getCode() == ResultCode.NOT_FOUND.getCode()
                    || response.getCode() == ResultCode.DATA_NOT_FOUND.getCode()) {
                throw new BusinessException(ResultCode.USER_NOT_FOUND, "接收者不存在");
            }
            throw degraded("getUserSimple", userId, response);
        }
        if (response.getData() == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "接收者不存在");
        }
        return response.getData();
    }

    @Override
    public boolean isBlocked(Long userId, Long targetUserId) {
        return requireBoolean("isBlocked", userId, targetUserId,
                userMessagingClient.isBlocked(userId, targetUserId));
    }

    @Override
    public boolean isFollowing(Long userId, Long targetUserId) {
        return requireBoolean("isFollowing", userId, targetUserId,
                userMessagingClient.isFollowing(userId, targetUserId));
    }

    @Override
    public boolean isStrangerMessageAllowed(Long userId) {
        ApiResponse<Boolean> response = userMessagingClient.isStrangerMessageAllowed(userId);
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw degraded("isStrangerMessageAllowed", userId, response);
        }
        return Boolean.TRUE.equals(response.getData());
    }

    private boolean requireBoolean(String operation, Long userId, Long targetUserId, ApiResponse<Boolean> response) {
        if (response == null || !response.isSuccess() || response.getData() == null) {
            throw degraded(operation, userId, targetUserId, response);
        }
        return Boolean.TRUE.equals(response.getData());
    }

    private BusinessException degraded(String operation, Long userId, ApiResponse<?> response) {
        return degraded(operation, userId, null, response);
    }

    private BusinessException degraded(String operation, Long userId, Long targetUserId, ApiResponse<?> response) {
        String code = response == null ? "null" : String.valueOf(response.getCode());
        String message = response == null ? "null response" : response.getMessage();
        log.warn("User messaging port call failed: operation={}, userId={}, targetUserId={}, code={}, message={}",
                operation, userId, targetUserId, code, message);
        return new BusinessException(ResultCode.SERVICE_DEGRADED, "用户服务已降级");
    }
}
