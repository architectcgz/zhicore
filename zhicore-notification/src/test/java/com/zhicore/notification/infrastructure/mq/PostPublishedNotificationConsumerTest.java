package com.zhicore.notification.infrastructure.mq;

import com.zhicore.common.mq.StatefulIdempotentHandler;
import com.zhicore.common.util.JsonUtils;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import com.zhicore.notification.application.service.broadcast.PostPublishedCampaignService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostPublishedNotificationConsumer 测试")
class PostPublishedNotificationConsumerTest {

    @Mock
    private StatefulIdempotentHandler idempotentHandler;

    @Mock
    private PostPublishedCampaignService campaignService;

    @Test
    @DisplayName("首次消费时应该规划 campaign")
    void shouldPlanCampaignWhenMessageConsumed() {
        PostPublishedIntegrationEvent event = new PostPublishedIntegrationEvent(
                "evt-post-published-1",
                Instant.parse("2026-03-26T08:00:00Z"),
                1001L,
                2002L,
                "标题",
                "摘要",
                Instant.parse("2026-03-26T08:00:01Z"),
                5L
        );
        PostPublishedNotificationConsumer consumer = new PostPublishedNotificationConsumer(
                idempotentHandler,
                campaignService
        );

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(1);
            runnable.run();
            return true;
        }).when(idempotentHandler).handleIdempotent(
                argThat(key ->
                        key != null
                                && key.contains(":notification-post-published-consumer:")
                                && key.contains(":ZhiCore-post-events:")
                                && key.endsWith(":" + event.getEventId())),
                any(Runnable.class)
        );

        consumer.onMessage(JsonUtils.toJson(event));

        verify(campaignService).planCampaign(any(PostPublishedIntegrationEvent.class));
    }
}
