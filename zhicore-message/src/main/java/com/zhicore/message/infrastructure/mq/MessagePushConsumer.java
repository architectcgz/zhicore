package com.zhicore.message.infrastructure.mq;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.domain.event.MessageSentEvent;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import com.zhicore.message.infrastructure.push.MultiChannelPushService;
import com.zhicore.message.infrastructure.push.PushMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 消息推送消费者
 * 消费消息发送事件，推送给接收者
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_MESSAGE_EVENTS,
        selectorExpression = TopicConstants.TAG_MESSAGE_SENT,
        consumerGroup = "message-push-consumer-group",
        consumeMode = ConsumeMode.ORDERLY
)
public class MessagePushConsumer extends AbstractEventConsumer<MessageSentEvent> {

    private final MultiChannelPushService pushService;
    private final UserServiceClient userServiceClient;

    public MessagePushConsumer(StatefulIdempotentHandler idempotentHandler,
                               MultiChannelPushService pushService,
                               UserServiceClient userServiceClient) {
        super(idempotentHandler, MessageSentEvent.class);
        this.pushService = pushService;
        this.userServiceClient = userServiceClient;
    }

    @Override
    protected void doHandle(MessageSentEvent event) {
        UserSimpleDTO sender = fetchUserInfo(event.getSenderId());

        PushMessage pushMessage = PushMessage.builder()
                .messageId(event.getMessageId())
                .conversationId(event.getConversationId())
                .senderId(event.getSenderId())
                .senderNickName(sender != null ? sender.getNickname() : null)
                .senderAvatarUrl(sender != null ? sender.getAvatarId() : null)
                .type(event.getMessageType())
                .contentPreview(event.getContentPreview())
                .sentAt(event.getSentAt())
                .pushType(PushMessage.PushType.NEW_MESSAGE)
                .build();

        pushService.push(String.valueOf(event.getReceiverId()), pushMessage);

        log.info("Message pushed to receiver: messageId={}, receiverId={}",
                event.getMessageId(), event.getReceiverId());
    }

    @Override
    protected void handleException(MessageSentEvent event, Exception e) {
        log.error("Failed to process message push: eventId={}, messageId={}, receiverId={}",
                event != null ? event.getEventId() : null,
                event != null ? event.getMessageId() : null,
                event != null ? event.getReceiverId() : null,
                e);
    }

    /**
     * 获取用户信息
     */
    private UserSimpleDTO fetchUserInfo(Long userId) {
        try {
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(userId);
            if (response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user info: userId={}", userId, e);
        }
        return null;
    }
}
