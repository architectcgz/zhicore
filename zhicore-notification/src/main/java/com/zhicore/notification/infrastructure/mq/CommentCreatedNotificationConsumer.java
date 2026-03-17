package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.comment.CommentCreatedIntegrationEvent;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.infrastructure.push.NotificationPushService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 评论创建通知消费者
 * 
 * 消费 CommentCreatedIntegrationEvent 事件，创建评论通知并推送给相关用户：
 * 1. 通知文章作者有新评论
 * 2. 如果是回复，通知被回复者
 *
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_COMMENT_EVENTS,
    selectorExpression = TopicConstants.TAG_COMMENT_CREATED,
    consumerGroup = NotificationConsumerGroups.COMMENT_CREATED_CONSUMER
)
public class CommentCreatedNotificationConsumer extends AbstractEventConsumer<CommentCreatedIntegrationEvent> {

    private final NotificationCommandService notificationService;
    private final NotificationPushService pushService;

    public CommentCreatedNotificationConsumer(StatefulIdempotentHandler idempotentHandler,
                                              NotificationCommandService notificationService,
                                              NotificationPushService pushService) {
        super(idempotentHandler, CommentCreatedIntegrationEvent.class);
        this.notificationService = notificationService;
        this.pushService = pushService;
    }

    @Override
    protected void doHandle(CommentCreatedIntegrationEvent event) {
        Long postOwnerId = event.getPostOwnerId();
        Long commentAuthorId = event.getCommentAuthorId();
        Long postId = event.getPostId();
        Long commentId = event.getCommentId();
        String commentContent = event.getCommentContent();
        Long replyToUserId = event.getReplyToUserId();

        // 1. 通知文章作者（如果评论者不是作者本人）
        if (!postOwnerId.equals(commentAuthorId)) {
            notificationService.createCommentNotificationIfAbsent(
                            NotificationEventKeys.notificationId(event.getEventId(), "comment-owner"),
                            postOwnerId,
                            commentAuthorId,
                            postId,
                            commentId,
                            commentContent
                    )
                    .ifPresent(notification -> pushService.push(String.valueOf(postOwnerId), notification));
            
            log.info("处理评论通知（通知文章作者）: postId={}, commentId={}, author={}, owner={}",
                    postId, commentId, commentAuthorId, postOwnerId);
        }

        // 2. 如果是回复，通知被回复者（如果被回复者不是评论者本人）
        if (replyToUserId != null && !replyToUserId.equals(commentAuthorId)) {
            notificationService.createReplyNotificationIfAbsent(
                            NotificationEventKeys.notificationId(event.getEventId(), "comment-reply"),
                            replyToUserId,
                            commentAuthorId,
                            commentId,
                            commentContent
                    )
                    .ifPresent(notification -> pushService.push(String.valueOf(replyToUserId), notification));
            
            log.info("处理回复通知: commentId={}, author={}, replyTo={}",
                    commentId, commentAuthorId, replyToUserId);
        }
    }
}
