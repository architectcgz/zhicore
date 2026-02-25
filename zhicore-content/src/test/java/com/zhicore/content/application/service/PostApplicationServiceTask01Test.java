package com.zhicore.content.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UploadServiceClient;
import com.zhicore.api.client.UserServiceClient;
import com.zhicore.content.application.command.handlers.DeletePostHandler;
import com.zhicore.content.application.command.handlers.PurgePostHandler;
import com.zhicore.content.application.command.handlers.UpdatePostContentHandler;
import com.zhicore.content.application.command.handlers.UpdatePostMetaHandler;
import com.zhicore.content.application.mapper.EventMapper;
import com.zhicore.content.application.port.messaging.EventPublisher;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.repo.ScheduledPublishEventRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.application.query.PostQuery;
import com.zhicore.content.application.workflow.CreateDraftWorkflow;
import com.zhicore.content.application.workflow.PublishPostWorkflow;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.domain.service.DraftService;
import com.zhicore.content.domain.service.TagDomainService;
import com.zhicore.content.infrastructure.alert.AlertService;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.content.infrastructure.messaging.OutboxEventTypes;
import com.zhicore.content.infrastructure.persistence.pg.entity.OutboxEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.entity.ScheduledPublishEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.OutboxEventMapper;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostApplicationServiceTask01Test {

    @Mock private PostRepository postRepository;
    @Mock private IdGeneratorFeignClient idGeneratorFeignClient;
    @Mock private DraftService draftService;
    @Mock private TagDomainService tagDomainService;
    @Mock private PostTagRepository postTagRepository;
    @Mock private TagRepository tagRepository;
    @Mock private UploadServiceClient uploadServiceClient;
    @Mock private UserServiceClient userServiceClient;
    @Mock private CreateDraftWorkflow createDraftWorkflow;
    @Mock private PublishPostWorkflow publishPostWorkflow;
    @Mock private UpdatePostMetaHandler updatePostMetaHandler;
    @Mock private UpdatePostContentHandler updatePostContentHandler;
    @Mock private DeletePostHandler deletePostHandler;
    @Mock private PurgePostHandler purgePostHandler;
    @Mock private PostQuery cacheAsidePostQuery;
    @Mock private PostContentStore postContentStore;
    @Mock private PostContentImageCleanupService postContentImageCleanupService;
    @Mock private EventPublisher domainEventPublisher;
    @Mock private IntegrationEventPublisher integrationEventPublisher;
    @Mock private EventMapper eventMapper;
    @Mock private ScheduledPublishEventRepository scheduledPublishEventRepository;

    @Spy private ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
    @Mock private AlertService alertService;
    @Mock private OutboxEventMapper outboxEventMapper;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private PostApplicationService postApplicationService;

    @Test
    void consumeScheduledPublish_retryExhausted_emitsDlqAndAlert() {
        scheduledPublishProperties.setMaxPublishRetries(0);

        Long postId = 123L;
        LocalDateTime dbNow = LocalDateTime.now();
        LocalDateTime scheduledAt = dbNow.minusSeconds(1);

        ScheduledPublishEventEntity record = new ScheduledPublishEventEntity();
        record.setEventId("evt-1");
        record.setPostId(postId);
        record.setScheduledAt(scheduledAt);
        record.setRescheduleRetryCount(0);
        record.setPublishRetryCount(0);
        record.setStatus(ScheduledPublishEventEntity.ScheduledPublishStatus.PENDING);

        when(scheduledPublishEventRepository.dbNow()).thenReturn(dbNow);
        when(scheduledPublishEventRepository.findByEventId("evt-1")).thenReturn(Optional.of(record));
        when(postRepository.findById(postId)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("boom"))
                .when(postRepository)
                .publishScheduledIfNeeded(postId, dbNow);

        PostScheduleExecuteIntegrationEvent message = new PostScheduleExecuteIntegrationEvent(
                "evt-1",
                Instant.now(),
                0L,
                postId,
                null,
                scheduledAt.atZone(ZoneId.systemDefault()).toInstant(),
                0
        );

        postApplicationService.consumeScheduledPublish(message);

        verify(alertService).alertScheduledPublishFailedAfterRetries(
                eq(postId),
                argThat(err -> err != null && err.contains("boom")),
                any()
        );

        ArgumentCaptor<OutboxEventEntity> captor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventMapper).insert(captor.capture());
        OutboxEventEntity entity = captor.getValue();

        assertThat(entity.getEventType()).isEqualTo(OutboxEventTypes.SCHEDULED_PUBLISH_DLQ);
        assertThat(entity.getAggregateId()).isEqualTo(postId);
        assertThat(entity.getStatus()).isEqualTo(OutboxEventEntity.OutboxStatus.PENDING);
        assertThat(entity.getPayload()).contains("\"postId\":", "\"retryCount\":");
    }
}
