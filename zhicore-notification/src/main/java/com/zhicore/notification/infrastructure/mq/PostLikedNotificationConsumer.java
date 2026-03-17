package com.zhicore.notification.infrastructure.mq;

import com.zhicore.api.event.post.PostLikedEvent;
import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 文章点赞通知消费者
 * 
 * 消费 PostLikedEvent 事件，创建点赞通知并推送给文章作者
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = TopicConstants.TAG_POST_LIKED,
    consumerGroup = NotificationConsumerGroups.POST_LIKED_CONSUMER
)
public class PostLikedNotificationConsumer extends AbstractEventConsumer<PostLikedEvent> {

    private final NotificationCommandService notificationService;
    private final NotificationPushService pushService;

    public PostLikedNotificationConsumer(StatefulIdempotentHandler idempotentHandler,
                                         NotificationCommandService notificationService,
                                         NotificationPushService pushService) {
        super(idempotentHandler, PostLikedEvent.class);
        this.notificationService = notificationService;
        this.pushService = pushService;
    }

    @Override
    protected void doHandle(PostLikedEvent event) {
        Long postOwnerId = event.getAuthorId();
        Long likerId = event.getUserId();
        Long postId = event.getPostId();

        // 不给自己发通知
        if (postOwnerId.equals(likerId)) {
            log.debug("跳过自己点赞自己的通知: postId={}, userId={}", postId, likerId);
            return;
        }

        notificationService.createLikeNotificationIfAbsent(
                        NotificationEventKeys.notificationId(event.getEventId(), "post-liked"),
                        postOwnerId,
                        likerId,
                        "post",
                        postId
                )
                .ifPresent(notification -> pushService.push(String.valueOf(postOwnerId), notification));

        log.info("处理文章点赞通知: postId={}, liker={}, owner={}",
                postId, likerId, postOwnerId);
    }
}
