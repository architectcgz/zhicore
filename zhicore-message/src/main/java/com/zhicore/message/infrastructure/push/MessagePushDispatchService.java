package com.zhicore.message.infrastructure.push;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.application.event.MessageSentPublishRequest;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 消息发送后的推送派发服务。
 *
 * <p>这是消息服务内部副作用，不再通过 RocketMQ 自发自收，
 * 而是由 outbox dispatcher 在事务提交后直接驱动。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePushDispatchService {

    private final MultiChannelPushService pushService;
    private final UserServiceClient userServiceClient;

    public void dispatchSentMessage(MessageSentPublishRequest request) {
        UserSimpleDTO sender = fetchUserInfo(request.getSenderId());

        PushMessage pushMessage = PushMessage.builder()
                .messageId(request.getMessageId())
                .conversationId(request.getConversationId())
                .senderId(request.getSenderId())
                .senderNickName(sender != null ? sender.getNickname() : null)
                .senderAvatarUrl(sender != null ? sender.getAvatarId() : null)
                .type(request.getMessageType())
                .contentPreview(request.getContentPreview())
                .sentAt(request.getSentAt())
                .pushType(PushMessage.PushType.NEW_MESSAGE)
                .build();

        pushService.push(String.valueOf(request.getReceiverId()), pushMessage);

        log.info("Message pushed to receiver: messageId={}, receiverId={}",
                request.getMessageId(), request.getReceiverId());
    }

    private UserSimpleDTO fetchUserInfo(Long userId) {
        try {
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(userId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user info: userId={}", userId, e);
        }
        return null;
    }
}
