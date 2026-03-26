package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.notification.application.service.broadcast.PostPublishedCampaignService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RocketMQMessageListener(
    topic = TopicConstants.TOPIC_POST_EVENTS,
    selectorExpression = TopicConstants.TAG_POST_PUBLISHED,
    consumerGroup = NotificationConsumerGroups.POST_PUBLISHED_CONSUMER
)
public class PostPublishedNotificationConsumer extends AbstractEventConsumer<PostPublishedIntegrationEvent> {

    private final PostPublishedCampaignService campaignService;

    public PostPublishedNotificationConsumer(StatefulIdempotentHandler idempotentHandler,
                                             PostPublishedCampaignService campaignService) {
        super(idempotentHandler, PostPublishedIntegrationEvent.class);
        this.campaignService = campaignService;
    }

    @Override
    protected void doHandle(PostPublishedIntegrationEvent event) {
        boolean planned = campaignService.planCampaign(event);
        log.info("处理发文广播规划: eventId={}, postId={}, planned={}",
                event.getEventId(), event.getPostId(), planned);
    }
}
