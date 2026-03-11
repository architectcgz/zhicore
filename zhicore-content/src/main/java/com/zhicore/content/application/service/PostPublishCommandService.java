package com.zhicore.content.application.service;

import com.zhicore.content.application.mapper.EventMapper;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.workflow.PublishPostWorkflow;
import com.zhicore.content.domain.event.DomainEvent;
import com.zhicore.content.domain.event.PostPublishedDomainEvent;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.integration.messaging.post.PostPublishedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 文章发布写服务。
 *
 * 收口发布文章用例编排与事件桥接，避免 PostWriteService 继续承载生命周期流转职责。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostPublishCommandService {

    private final PublishPostWorkflow publishPostWorkflow;
    private final EventPublisher domainEventPublisher;
    private final IntegrationEventPublisher integrationEventPublisher;
    private final EventMapper eventMapper;

    @Transactional
    public void publishPost(Long userId, Long postId) {
        List<DomainEvent<?>> domainEvents = publishPostWorkflow.execute(
                PostId.of(postId),
                UserId.of(userId)
        );

        publishDomainEvents(postId, domainEvents);
        publishIntegrationEvents(postId, domainEvents);

        log.info("Post published: postId={}, userId={}", postId, userId);
    }

    private void publishDomainEvents(Long postId, List<DomainEvent<?>> domainEvents) {
        if (domainEvents.isEmpty()) {
            return;
        }

        @SuppressWarnings("unchecked")
        List<DomainEvent> rawEvents = (List) domainEvents;
        domainEventPublisher.publishBatch(rawEvents);
        log.info("Published {} domain events for post: postId={}", domainEvents.size(), postId);
    }

    private void publishIntegrationEvents(Long postId, List<DomainEvent<?>> domainEvents) {
        for (DomainEvent<?> domainEvent : domainEvents) {
            if (!(domainEvent instanceof PostPublishedDomainEvent publishedEvent)) {
                continue;
            }

            PostPublishedIntegrationEvent integrationEvent = eventMapper.toIntegrationEvent(publishedEvent);
            integrationEventPublisher.publish(integrationEvent);
            log.info("Published integration event to Outbox: eventId={}, postId={}",
                    integrationEvent.getEventId(), postId);
        }
    }
}
