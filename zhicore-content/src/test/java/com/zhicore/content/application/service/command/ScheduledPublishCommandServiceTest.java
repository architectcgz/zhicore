package com.zhicore.content.application.service.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.tx.TransactionCommitSignal;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.OutboxEventTypes;
import com.zhicore.content.application.model.ScheduledPublishEventRecord;
import com.zhicore.content.application.port.alert.ContentAlertPort;
import com.zhicore.content.application.port.messaging.IntegrationEventPublisher;
import com.zhicore.content.application.port.policy.ScheduledPublishPolicy;
import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.ScheduledPublishEventStore;
import com.zhicore.content.application.service.OwnedPostLoadService;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.config.ScheduledPublishProperties;
import com.zhicore.content.infrastructure.messaging.OutboxDispatchTrigger;
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
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduledPublishCommandServiceTest {

    @Mock private OwnedPostLoadService ownedPostLoadService;
    @Mock private PostRepository postRepository;
    @Mock private IntegrationEventPublisher integrationEventPublisher;
    @Mock private ScheduledPublishEventStore scheduledPublishEventStore;
    @Mock private ScheduledPublishPolicy scheduledPublishPolicy;
    @Mock private ContentAlertPort alertService;
    @Mock private OutboxEventStore outboxEventStore;
    @Mock private TransactionCommitSignal transactionCommitSignal;
    @Mock private OutboxDispatchTrigger outboxDispatchTrigger;
    @Spy private ScheduledPublishProperties scheduledPublishProperties = new ScheduledPublishProperties();
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ScheduledPublishCommandService scheduledPublishCommandService;

    @Test
    void schedulePublishShouldSaveRecordAndPublishEvents() {
        Long userId = 1001L;
        Long postId = 123L;
        OffsetDateTime dbNow = OffsetDateTime.now();
        OffsetDateTime scheduledAt = dbNow.plusMinutes(5);
        Post post = Post.createDraft(PostId.of(postId), UserId.of(userId), "title");

        when(ownedPostLoadService.load(postId, userId)).thenReturn(post);
        when(postRepository.findById(postId)).thenReturn(Optional.of(post), Optional.of(post));
        when(scheduledPublishEventStore.dbNow()).thenReturn(dbNow);

        scheduledPublishCommandService.schedulePublish(userId, postId, scheduledAt);

        verify(postRepository).update(post);
        verify(scheduledPublishEventStore).markTerminalByPostId(
                eq(postId),
                eq(ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED),
                eq(dbNow),
                eq("被新的定时发布配置覆盖")
        );
        verify(scheduledPublishEventStore).save(any(ScheduledPublishEventRecord.class));

        ArgumentCaptor<IntegrationEvent> eventCaptor = ArgumentCaptor.forClass(IntegrationEvent.class);
        verify(integrationEventPublisher, times(2)).publish(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .anyMatch(PostScheduleExecuteIntegrationEvent.class::isInstance)
                .anyMatch(PostScheduledIntegrationEvent.class::isInstance);
    }

    @Test
    void consumeScheduledPublishShouldEmitDlqAndAlertWhenRetriesExhausted() {
        when(scheduledPublishPolicy.maxPublishRetries()).thenReturn(0);

        Long postId = 123L;
        OffsetDateTime dbNow = OffsetDateTime.now();
        OffsetDateTime scheduledAt = dbNow.minusSeconds(1);

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .eventId("task-evt-1")
                .triggerEventId("evt-1")
                .postId(postId)
                .scheduledAt(scheduledAt)
                .nextAttemptAt(dbNow)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .build();

        when(scheduledPublishEventStore.dbNow()).thenReturn(dbNow);
        when(scheduledPublishEventStore.findByEventId("task-evt-1")).thenReturn(Optional.of(record));
        when(scheduledPublishEventStore.claimForConsumption(eq("task-evt-1"), eq(dbNow), any(), any()))
                .thenReturn(Optional.of(record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PROCESSING)));
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
                scheduledAt.toInstant(),
                0,
                "task-evt-1"
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
        verify(transactionCommitSignal).afterCommit(any());
    }

    @Test
    void consumeScheduledPublishShouldStayPendingAndWaitForCompensationWhenRescheduleRetriesExhausted() {
        when(scheduledPublishPolicy.maxRescheduleRetries()).thenReturn(0);

        Long postId = 456L;
        OffsetDateTime dbNow = OffsetDateTime.now();
        OffsetDateTime scheduledAt = dbNow.plusMinutes(5);

        ScheduledPublishEventRecord record = ScheduledPublishEventRecord.builder()
                .id(1L)
                .eventId("task-evt-future")
                .triggerEventId("evt-future")
                .postId(postId)
                .scheduledAt(scheduledAt)
                .nextAttemptAt(dbNow)
                .rescheduleRetryCount(0)
                .publishRetryCount(0)
                .status(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING)
                .build();

        when(scheduledPublishEventStore.dbNow()).thenReturn(dbNow);
        when(scheduledPublishEventStore.findByEventId("task-evt-future")).thenReturn(Optional.of(record));
        when(scheduledPublishEventStore.claimForConsumption(eq("task-evt-future"), eq(dbNow), any(), any()))
                .thenReturn(Optional.of(record.withStatus(ScheduledPublishEventRecord.ScheduledPublishStatus.PROCESSING)));

        PostScheduleExecuteIntegrationEvent message = new PostScheduleExecuteIntegrationEvent(
                "evt-future",
                Instant.now(),
                0L,
                postId,
                1001L,
                scheduledAt.toInstant(),
                5,
                "task-evt-future"
        );

        scheduledPublishCommandService.consumeScheduledPublish(message);

        ArgumentCaptor<ScheduledPublishEventRecord> recordCaptor =
                ArgumentCaptor.forClass(ScheduledPublishEventRecord.class);
        verify(scheduledPublishEventStore).update(recordCaptor.capture());
        ScheduledPublishEventRecord updated = recordCaptor.getValue();

        assertThat(updated.getStatus()).isEqualTo(ScheduledPublishEventRecord.ScheduledPublishStatus.PENDING);
        assertThat(updated.getClaimedAt()).isNull();
        assertThat(updated.getClaimedBy()).isNull();
        assertThat(updated.getNextAttemptAt()).isNotNull();
        assertThat(updated.getLastError()).contains("等待补偿扫描接管");
        verify(integrationEventPublisher, never()).publish(any());
    }

    @Test
    void cancelScheduleShouldMarkTaskAsSucceeded() {
        Long userId = 1001L;
        Long postId = 987L;
        OffsetDateTime dbNow = OffsetDateTime.now();
        Post post = Post.createDraft(PostId.of(postId), UserId.of(userId), "title");
        post.schedulePublish(dbNow.plusMinutes(10));

        when(ownedPostLoadService.load(postId, userId)).thenReturn(post);
        when(scheduledPublishEventStore.dbNow()).thenReturn(dbNow);

        scheduledPublishCommandService.cancelSchedule(userId, postId);

        verify(postRepository).update(post);
        verify(scheduledPublishEventStore).markTerminalByPostId(
                postId,
                ScheduledPublishEventRecord.ScheduledPublishStatus.SUCCEEDED,
                dbNow,
                null
        );
    }
}
