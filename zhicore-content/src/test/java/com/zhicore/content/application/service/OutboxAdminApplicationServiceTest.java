package com.zhicore.content.application.service;

import com.zhicore.common.exception.ResourceNotFoundException;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.admin.outbox.OutboxFailedPageResponse;
import com.zhicore.content.application.dto.admin.outbox.OutboxRetryResponse;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.model.OutboxRetryAuditRecord;
import com.zhicore.content.application.port.store.OutboxEventStore;
import com.zhicore.content.application.port.store.OutboxRetryAuditStore;
import com.zhicore.content.application.service.command.OutboxAdminCommandService;
import com.zhicore.content.application.service.query.OutboxAdminQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxAdminServicesTest {

    @Mock
    private OutboxEventStore outboxEventStore;

    @Mock
    private OutboxRetryAuditStore outboxRetryAuditStore;

    @InjectMocks
    private OutboxAdminQueryService outboxAdminQueryService;

    @InjectMocks
    private OutboxAdminCommandService outboxAdminCommandService;

    @Test
    void listFailedShouldNormalizePaginationAndMapRecords() {
        OutboxEventRecord record = failedRecord();
        when(outboxEventStore.findFailed(1, 100, "TYPE_A"))
                .thenReturn(PageResult.of(1, 100, 1, List.of(record)));

        OutboxFailedPageResponse response = outboxAdminQueryService.listFailed(0, 1000, "  TYPE_A  ");

        assertEquals(1, response.getPage());
        assertEquals(100, response.getSize());
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getItems().size());
        assertEquals("EVENT_1", response.getItems().get(0).getEventId());
        assertEquals("TYPE_A", response.getItems().get(0).getEventType());
        verify(outboxEventStore).findFailed(1, 100, "TYPE_A");
    }

    @Test
    void retryFailedShouldResetEventAndWriteAudit() {
        OutboxEventRecord record = failedRecord();
        when(outboxEventStore.findByEventId("EVENT_1")).thenReturn(Optional.of(record));
        when(outboxRetryAuditStore.countRecentRetries(eq("EVENT_1"), any())).thenReturn(0L);

        OutboxRetryResponse response = outboxAdminCommandService.retryFailed(" EVENT_1 ", 2001L, "manual retry");

        assertEquals("EVENT_1", response.getEventId());
        assertEquals("PENDING", response.getStatus());

        ArgumentCaptor<OutboxEventRecord> eventCaptor = ArgumentCaptor.forClass(OutboxEventRecord.class);
        verify(outboxEventStore).update(eventCaptor.capture());
        OutboxEventRecord updatedRecord = eventCaptor.getValue();
        assertEquals(OutboxEventRecord.OutboxStatus.PENDING, updatedRecord.getStatus());
        assertEquals(0, updatedRecord.getRetryCount());
        assertNull(updatedRecord.getLastError());
        assertNull(updatedRecord.getDispatchedAt());

        ArgumentCaptor<OutboxRetryAuditRecord> auditCaptor = ArgumentCaptor.forClass(OutboxRetryAuditRecord.class);
        verify(outboxRetryAuditStore).save(auditCaptor.capture());
        OutboxRetryAuditRecord auditRecord = auditCaptor.getValue();
        assertEquals("EVENT_1", auditRecord.getEventId());
        assertEquals(2001L, auditRecord.getOperatorId());
        assertEquals("manual retry", auditRecord.getReason());
        assertEquals("ACCEPTED", auditRecord.getResult());
    }

    @Test
    void retryFailedShouldRejectWhenRetryTooFrequent() {
        when(outboxEventStore.findByEventId("EVENT_1")).thenReturn(Optional.of(failedRecord()));
        when(outboxRetryAuditStore.countRecentRetries(eq("EVENT_1"), any())).thenReturn(1L);

        assertThrows(TooManyRequestsException.class,
                () -> outboxAdminCommandService.retryFailed("EVENT_1", 2001L, "manual retry"));

        verify(outboxEventStore, never()).update(any());
        verify(outboxRetryAuditStore, never()).save(any());
    }

    @Test
    void retryFailedShouldRejectWhenStatusIsNotFailed() {
        OutboxEventRecord record = failedRecord().withStatus(OutboxEventRecord.OutboxStatus.PENDING);
        when(outboxEventStore.findByEventId("EVENT_1")).thenReturn(Optional.of(record));
        when(outboxRetryAuditStore.countRecentRetries(eq("EVENT_1"), any())).thenReturn(0L);

        assertThrows(IllegalArgumentException.class,
                () -> outboxAdminCommandService.retryFailed("EVENT_1", 2001L, "manual retry"));

        verify(outboxEventStore, never()).update(any());
        verify(outboxRetryAuditStore, never()).save(any());
    }

    @Test
    void retryFailedShouldRejectWhenEventMissing() {
        when(outboxEventStore.findByEventId("EVENT_404")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> outboxAdminCommandService.retryFailed("EVENT_404", 2001L, "manual retry"));

        verify(outboxRetryAuditStore, never()).countRecentRetries(any(), any());
        verify(outboxEventStore, never()).update(any());
    }

    private OutboxEventRecord failedRecord() {
        Instant now = Instant.parse("2026-03-10T10:15:30Z");
        return OutboxEventRecord.builder()
                .id(1L)
                .eventId("EVENT_1")
                .eventType("TYPE_A")
                .aggregateId(101L)
                .aggregateVersion(2L)
                .schemaVersion(1)
                .payload("{\"k\":\"v\"}")
                .occurredAt(now.minusSeconds(60))
                .createdAt(now.minusSeconds(30))
                .updatedAt(now)
                .dispatchedAt(now.minusSeconds(10))
                .retryCount(3)
                .lastError("boom")
                .status(OutboxEventRecord.OutboxStatus.FAILED)
                .build();
    }
}
