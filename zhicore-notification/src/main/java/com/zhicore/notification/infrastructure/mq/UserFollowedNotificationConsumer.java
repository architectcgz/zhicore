package com.zhicore.notification.infrastructure.mq;

import com.zhicore.api.event.user.UserFollowedEvent;
import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.notification.application.service.NotificationApplicationService;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 用户关注通知消费者
 * 
 * 消费 UserFollowedEvent 事件，创建关注通知并推送给被关注者
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_USER_EVENTS,
    selectorExpression = TopicConstants.TAG_USER_FOLLOWED,
    consumerGroup = NotificationConsumerGroups.USER_FOLLOWED_CONSUMER
)
public class UserFollowedNotificationConsumer extends AbstractEventConsumer<UserFollowedEvent> {

    private final NotificationApplicationService notificationService;
    private final NotificationPushService pushService;

    public UserFollowedNotificationConsumer(StatefulIdempotentHandler idempotentHandler,
                                            NotificationApplicationService notificationService,
                                            NotificationPushService pushService) {
        super(idempotentHandler, UserFollowedEvent.class);
        this.notificationService = notificationService;
        this.pushService = pushService;
    }

    @Override
    protected void doHandle(UserFollowedEvent event) {
        Long followerId = event.getFollowerId();
        Long followingId = event.getFollowingId();

        // 创建关注通知
        Notification notification = notificationService.createFollowNotification(
            followingId,  // 被关注者收到通知
            followerId    // 关注者是触发者
        );

        // 实时推送
        pushService.push(String.valueOf(followingId), notification);

        log.info("处理关注通知: follower={}, following={}",
                followerId, followingId);
    }
}
