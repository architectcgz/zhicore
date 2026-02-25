package com.zhicore.message.application.service;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
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

    private final UserServiceClient userServiceClient;

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
            throw new BusinessException("不能给自己发送消息");
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
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(String.valueOf(receiverId));
            if (response == null || !response.isSuccess() || response.getData() == null) {
                log.info("Message rejected: receiver {} does not exist", receiverId);
                throw new BusinessException("接收者不存在");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 服务调用失败时，为了安全起见，拒绝发送
            log.error("Failed to check receiver existence for {}: {}", receiverId, e.getMessage());
            throw new BusinessException("无法验证接收者，请稍后重试");
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
            ApiResponse<Boolean> response = userServiceClient.isBlocked(String.valueOf(receiverId), String.valueOf(senderId));
            if (response.isSuccess() && Boolean.TRUE.equals(response.getData())) {
                log.info("Message blocked: sender {} is blocked by receiver {}", senderId, receiverId);
                throw new BusinessException("您已被对方拉黑，无法发送消息");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 服务调用失败时，默认允许发送（降级策略）
            log.warn("Failed to check block status, allowing message: senderId={}, receiverId={}", 
                    senderId, receiverId, e);
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
            // 检查是否是陌生人（未互相关注）
            ApiResponse<Boolean> strangerResponse = userServiceClient.isStranger(String.valueOf(senderId), String.valueOf(receiverId));
            boolean isStranger = strangerResponse.isSuccess() && Boolean.TRUE.equals(strangerResponse.getData());

            if (!isStranger) {
                // 不是陌生人，允许发送
                return;
            }

            // 检查接收者是否允许陌生人消息
            ApiResponse<Boolean> allowedResponse = userServiceClient.isStrangerMessageAllowed(String.valueOf(receiverId));
            boolean strangerMessageAllowed = !allowedResponse.isSuccess() || 
                    Boolean.TRUE.equals(allowedResponse.getData());

            if (!strangerMessageAllowed) {
                log.info("Stranger message blocked: sender {} to receiver {}", senderId, receiverId);
                throw new BusinessException("对方不接收陌生人消息，请先关注对方");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 服务调用失败时，默认允许发送（降级策略）
            log.warn("Failed to check stranger message setting, allowing message: senderId={}, receiverId={}", 
                    senderId, receiverId, e);
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
            ApiResponse<Boolean> response = userServiceClient.isStranger(String.valueOf(userId1), String.valueOf(userId2));
            return response.isSuccess() && Boolean.TRUE.equals(response.getData());
        } catch (Exception e) {
            log.warn("Failed to check stranger status: userId1={}, userId2={}", userId1, userId2, e);
            return false;
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
            ApiResponse<Boolean> response = userServiceClient.isBlocked(String.valueOf(userId), String.valueOf(targetUserId));
            return response.isSuccess() && Boolean.TRUE.equals(response.getData());
        } catch (Exception e) {
            log.warn("Failed to check block status: userId={}, targetUserId={}", userId, targetUserId, e);
            return false;
        }
    }
}
