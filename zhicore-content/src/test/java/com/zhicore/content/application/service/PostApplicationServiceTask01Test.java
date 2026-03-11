package com.zhicore.content.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.OutboxEventTypes;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.policy.ScheduledPublishPolicy;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.integration.messaging.IntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduleExecuteIntegrationEvent;
import com.zhicore.integration.messaging.post.PostScheduledIntegrationEvent;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostApplicationServiceTask01Test {

    @Mock private PostRepository postRepository;
    @Mock private IntegrationEventPublisher integrationEventPublisher;
    @Mock private ScheduledPublishEventStore scheduledPublishEventStore;
    @Mock private ScheduledPublishPolicy scheduledPublishPolicy;
    @Mock private ContentAlertPort alertService;
    @Mock private OutboxEventStore outboxEventStore;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ScheduledPublishCommandService scheduledPublishCommandService;

    @Test
    void schedulePublish_shouldSaveRecordAndPublishEvents() {
        Long userId = 1001L;
        Long postId = 123L;
        LocalDateTime dbNow = LocalDateTime.now();
        LocalDateTime scheduledAt = dbNow.plusMinutes(5);
        Post post = Post.createDraft(PostId.of(postId), UserId.of(userId), "title");

        when(postRepository.findById(postId)).thenReturn(Optional.of(post), Optional.of(post));
        when(scheduledPublishEventStore.dbNow()).thenReturn(dbNow);

        scheduledPublishCommandService.schedulePublish(userId, postId, scheduledAt);

        verify(postRepository).update(post);
        verify(scheduledPublishEventStore).save(any(ScheduledPublishEventRecord.class));

        ArgumentCaptor<IntegrationEvent> eventCaptor = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(integrationEventPublisher, times(2)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anyMatch(PostScheduleExecuteIntegrationEvent.class::isInstance)
                .anyMatch(PostScheduledIntegrationEvent.class::isInstance);
    }

    @Test
    void consumeScheduledPublish_retryExhausted_emitsDlqAndAlert() {
        when(scheduledPublishPolicy.maxPublishRetries()).thenReturn(0);

        Long postId = 123L;
        LocalDateTime dbNow = LocalDateTime.now();
        LocalDateTime scheduledAt = dbNow.minusSeconds(1);

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId("evt-1")
                .postId(postId)
                .scheduledAt(scheduledAt)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .build();

        when(scheduledPublishEventStore.dbNow()).thenReturn(dbNow);
        when(scheduledPublishEventStore.findByEventId("evt-1")).thenReturn(Optional.of(record));
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

        scheduledPublishCommandService.consumeScheduledPublish(message);

        verify(alertService).alertScheduledPublishFailedAfterRetries(
                eq(postId),
                argThat(err -> err != null && err.contains("boom")),
                any()
        );

        ArgumentCaptor<OutboxEventRecord> captor = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(outboxEventStore).save(captor.capture());
        OutboxEventRecord entity = captor.getValue();

        assertThat(entity.getEventType()).isEqualTo(OutboxEventTypes.SCHEDULED_PUBLISH_DLQ);
        assertThat(entity.getAggregateId()).isEqualTo(postId);
        assertThat(entity.getStatus()).isEqualTo(OutboxEventRecord.OutboxStatus.PENDING);
        assertThat(entity.getPayload()).contains("\"postId\":", "\"retryCount\":");
    }
}
