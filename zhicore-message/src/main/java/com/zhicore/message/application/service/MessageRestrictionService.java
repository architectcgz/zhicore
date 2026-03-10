package com.zhicore.message.application.service;

import com.zhicore.api.client.UserMessagingClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 消息限制服务
 * 处理拉黑用户消息阻止和陌生人消息限制
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageRestrictionService {

    private static final String RECEIVER_VALIDATION_FAILED_MESSAGE = "无法验证接收者，请稍后重试";
    private static final String BLOCK_STATUS_VALIDATION_FAILED_MESSAGE = "无法验证拉黑状态，请稍后重试";
    private static final String STRANGER_STATUS_VALIDATION_FAILED_MESSAGE = "无法验证关注关系，请稍后重试";
    private static final String STRANGER_SETTING_VALIDATION_FAILED_MESSAGE = "无法验证陌生人消息设置，请稍后重试";

    private final UserMessagingClient userServiceClient;

    /**
     * 检查是否可以发送消息
     *
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @throws BusinessException 如果不能发送消息
     */
    public void checkCanSendMessage(Long senderId, Long receiverId) {
        // 不能给自己发消息
        if (senderId.equals(receiverId)) {
            throw new BusinessException(ResultCode.CANNOT_MESSAGE_SELF);
        }

        // 检查接收者是否存在
        checkReceiverExists(receiverId);

        // 检查是否被拉黑
        checkNotBlocked(senderId, receiverId);

        // 检查陌生人消息限制
        checkStrangerMessageAllowed(senderId, receiverId);
    }

    /**
     * 检查接收者是否存在
     *
     * @param receiverId 接收者ID
     * @throws BusinessException 如果接收者不存在
     */
    private void checkReceiverExists(Long receiverId) {
        try {
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(receiverId);
            if (response == null) {
                log.warn("Receiver lookup returned null response: receiverId={}", receiverId);
                throw new BusinessException(ResultCode.SERVICE_DEGRADED, RECEIVER_VALIDATION_FAILED_MESSAGE);
            }
            if (!response.isSuccess()) {
                if (isNotFound(response.getCode())) {
                    log.info("Message rejected: receiver {} does not exist", receiverId);
                    throw new BusinessException(ResultCode.USER_NOT_FOUND, "接收者不存在");
                }
                log.warn("Receiver lookup failed: receiverId={}, code={}, message={}",
                        receiverId, response.getCode(), response.getMessage());
                throw new BusinessException(ResultCode.SERVICE_DEGRADED, RECEIVER_VALIDATION_FAILED_MESSAGE);
            }
            if (response.getData() == null) {
                log.info("Message rejected: receiver {} does not exist", receiverId);
                throw new BusinessException(ResultCode.USER_NOT_FOUND, "接收者不存在");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to check receiver existence for {}: {}", receiverId, e.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, RECEIVER_VALIDATION_FAILED_MESSAGE);
        }
    }

    /**
     * 检查发送者是否被接收者拉黑
     *
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @throws BusinessException 如果被拉黑
     */
    public void checkNotBlocked(Long senderId, Long receiverId) {
        try {
            ApiResponse<Boolean> response = userServiceClient.isBlocked(receiverId, senderId);
            boolean blocked = requireBooleanResponse(response, BLOCK_STATUS_VALIDATION_FAILED_MESSAGE,
                    "block status", senderId, receiverId);
            if (blocked) {
                log.info("Message blocked: sender {} is blocked by receiver {}", senderId, receiverId);
                throw new BusinessException(ResultCode.USER_BLOCKED_CANNOT_MESSAGE, "您已被对方拉黑，无法发送消息");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check block status: senderId={}, receiverId={}",
                    senderId, receiverId, e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, BLOCK_STATUS_VALIDATION_FAILED_MESSAGE);
        }
    }

    /**
     * 检查陌生人消息是否被允许
     *
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @throws BusinessException 如果陌生人消息被限制
     */
    public void checkStrangerMessageAllowed(Long senderId, Long receiverId) {
        try {
            boolean isStranger = isStranger(senderId, receiverId);

            if (!isStranger) {
                return;
            }

            ApiResponse<Boolean> allowedResponse = userServiceClient.isStrangerMessageAllowed(receiverId);
            boolean strangerMessageAllowed = requireBooleanResponse(allowedResponse,
                    STRANGER_SETTING_VALIDATION_FAILED_MESSAGE, "stranger message setting", senderId, receiverId);

            if (!strangerMessageAllowed) {
                log.info("Stranger message blocked: sender {} to receiver {}", senderId, receiverId);
                throw new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "对方不接收陌生人消息，请先关注对方");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check stranger message setting: senderId={}, receiverId={}",
                    senderId, receiverId, e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, STRANGER_SETTING_VALIDATION_FAILED_MESSAGE);
        }
    }

    /**
     * 检查是否是陌生人
     *
     * @param userId1 用户1 ID
     * @param userId2 用户2 ID
     * @return 是否是陌生人
     */
    public boolean isStranger(Long userId1, Long userId2) {
        try {
            boolean user1FollowingUser2 = requireBooleanResponse(
                    userServiceClient.isFollowing(userId1, userId2),
                    STRANGER_STATUS_VALIDATION_FAILED_MESSAGE, "follow status", userId1, userId2);
            boolean user2FollowingUser1 = requireBooleanResponse(
                    userServiceClient.isFollowing(userId2, userId1),
                    STRANGER_STATUS_VALIDATION_FAILED_MESSAGE, "follow status", userId2, userId1);
            return !user1FollowingUser2 || !user2FollowingUser1;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check stranger status: userId1={}, userId2={}", userId1, userId2, e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, STRANGER_STATUS_VALIDATION_FAILED_MESSAGE);
        }
    }

    /**
     * 检查用户是否被拉黑
     *
     * @param userId 用户ID
     * @param targetUserId 目标用户ID
     * @return 是否被拉黑
     */
    public boolean isBlocked(Long userId, Long targetUserId) {
        try {
            ApiResponse<Boolean> response = userServiceClient.isBlocked(userId, targetUserId);
            return requireBooleanResponse(response, BLOCK_STATUS_VALIDATION_FAILED_MESSAGE,
                    "block status", userId, targetUserId);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to check block status: userId={}, targetUserId={}", userId, targetUserId, e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, BLOCK_STATUS_VALIDATION_FAILED_MESSAGE);
        }
    }

    private boolean requireBooleanResponse(ApiResponse<Boolean> response, String failureMessage,
                                           String validationName, Long senderId, Long receiverId) {
        if (response == null) {
            log.warn("Message restriction validation returned null: validation={}, senderId={}, receiverId={}",
                    validationName, senderId, receiverId);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, failureMessage);
        }
        if (!response.isSuccess() || response.getData() == null) {
            log.warn("Message restriction validation failed: validation={}, senderId={}, receiverId={}, code={}, message={}",
                    validationName, senderId, receiverId, response.getCode(), response.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, failureMessage);
        }
        return Boolean.TRUE.equals(response.getData());
    }

    private boolean isNotFound(int code) {
        return code == ResultCode.NOT_FOUND.getCode()
                || code == ResultCode.DATA_NOT_FOUND.getCode()
                || code == ResultCode.USER_NOT_FOUND.getCode();
    }
}
