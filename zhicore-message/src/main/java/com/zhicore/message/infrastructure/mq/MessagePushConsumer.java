package com.zhicore.message.infrastructure.mq;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.message.domain.event.MessageSentEvent;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import com.zhicore.message.infrastructure.push.MultiChannelPushService;
import com.zhicore.message.infrastructure.push.PushMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 消息推送消费者
 * 消费消息发送事件，推送给接收者
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TopicConstants.TOPIC_MESSAGE_EVENTS,
        selectorExpression = TopicConstants.TAG_MESSAGE_SENT,
        consumerGroup = "message-push-consumer-group",
        consumeMode = ConsumeMode.ORDERLY
)
public class MessagePushConsumer implements RocketMQListener<String> {

    private final MultiChannelPushService pushService;
    private final UserServiceClient userServiceClient;

    @Override
    public void onMessage(String messageJson) {
        try {
            MessageSentEvent event = JsonUtils.fromJson(messageJson, MessageSentEvent.class);
            if (event == null) {
                log.warn("Failed to parse MessageSentEvent: {}", messageJson);
                return;
            }

            // 获取发送者信息
            UserSimpleDTO sender = fetchUserInfo(event.getSenderId());

            // 构建推送消息
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

            // 推送给接收者
            pushService.push(String.valueOf(event.getReceiverId()), pushMessage);

            log.info("Message pushed to receiver: messageId={}, receiverId={}", 
                    event.getMessageId(), event.getReceiverId());

        } catch (Exception e) {
            log.error("Failed to process message push: {}", messageJson, e);
            // 不抛出异常，避免消息重试
        }
    }

    /**
     * 获取用户信息
     */
    private UserSimpleDTO fetchUserInfo(Long userId) {
        try {
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(String.valueOf(userId));
            if (response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user info: userId={}", userId, e);
        }
        return null;
    }
}
