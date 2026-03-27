package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.notification.application.service.campaign.NotificationCampaignCommandService;
import com.zhicore.notification.application.service.campaign.NotificationCampaignShardWorker;
import com.zhicore.notification.domain.model.NotificationCampaign;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublishedNotificationConsumer 测试")
class PostPublishedNotificationConsumerTest {

    @Mock
    private StatefulIdempotentHandler idempotentHandler;

    @Mock
    private NotificationCampaignCommandService campaignCommandService;

    @Mock
    private NotificationCampaignShardWorker shardWorker;

    @Test
    @DisplayName("首次消费时应该创建 campaign 并驱动 shard worker")
    void shouldCreateCampaignAndDrainShards() {
        PostPublishedNotificationConsumer consumer = new PostPublishedNotificationConsumer(
                idempotentHandler, campaignCommandService, shardWorker);
        PostPublishedIntegrationEvent event = new PostPublishedIntegrationEvent(
                "evt-1", Instant.parse("2026-03-27T10:00:00Z"), 101L, 202L,
                Instant.parse("2026-03-27T10:00:00Z"), 1L);
        NotificationCampaign campaign = NotificationCampaign.create(501L, event.getEventId(), 101L, 202L);

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(eq(event.getEventId()), any(Runnable.class));
        when(campaignCommandService.createOrLoad(any(PostPublishedIntegrationEvent.class))).thenReturn(campaign);

        consumer.onMessage(com.zhicore.common.util.JsonUtils.toJson(event));

        verify(campaignCommandService).createOrLoad(any(PostPublishedIntegrationEvent.class));
        verify(shardWorker).drain(campaign);
    }
}
