package com.zhicore.content.application.service.command;

import com.zhicore.content.application.mapper.EventMapper;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.workflow.PublishPostWorkflow;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostPublishCommandServiceTest {

    @Mock
    private PublishPostWorkflow publishPostWorkflow;

    @Mock
    private EventPublisher domainEventPublisher;

    @Mock
    private IntegrationEventPublisher integrationEventPublisher;

    @Mock
    private EventMapper eventMapper;

    @InjectMocks
    private PostPublishCommandService postPublishCommandService;

    @Test
    void shouldPublishDomainAndIntegrationEvents() {
        Long userId = 1001L;
        Long postId = 2001L;
        PostPublishedDomainEvent domainEvent = new PostPublishedDomainEvent(
                "evt-published",
                Instant.now(),
                PostId.of(postId),
                userId,
                Instant.now(),
                5L
        );
        PostPublishedIntegrationEvent integrationEvent = new PostPublishedIntegrationEvent(
                "evt-published",
                Instant.now(),
                postId,
                userId,
                Instant.now(),
                5L
        );

        when(publishPostWorkflow.execute(PostId.of(postId), UserId.of(userId))).thenReturn(List.of(domainEvent));
        when(eventMapper.toIntegrationEvent(domainEvent)).thenReturn(integrationEvent);

        postPublishCommandService.publishPost(userId, postId);

        verify(domainEventPublisher).publishBatch(any());
        verify(integrationEventPublisher).publish(integrationEvent);
    }
}
