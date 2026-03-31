package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.AbstractEventConsumer;
import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.mq.TopicConstants;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.notification.application.service.campaign.NotificationCampaignCommandService;
import com.zhicore.notification.application.service.campaign.NotificationCampaignShardWorker;
import com.zhicore.notification.domain.model.NotificationCampaign;
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

    private final NotificationCampaignCommandService campaignCommandService;
    private final NotificationCampaignShardWorker shardWorker;

    public PostPublishedNotificationConsumer(StatefulIdempotentHandler idempotentHandler,
                                             NotificationCampaignCommandService campaignCommandService,
                                             NotificationCampaignShardWorker shardWorker) {
        super(idempotentHandler, PostPublishedIntegrationEvent.class);
        this.campaignCommandService = campaignCommandService;
        this.shardWorker = shardWorker;
    }

    @Override
    protected void doHandle(PostPublishedIntegrationEvent event) {
        NotificationCampaign campaign = campaignCommandService.createOrLoad(event);
        shardWorker.drain(campaign);
        log.info("处理发布广播通知: eventId={}, campaignId={}, postId={}, authorId={}",
                event.getEventId(), campaign.getId(), event.getPostId(), event.getAuthorId());
    }
}
