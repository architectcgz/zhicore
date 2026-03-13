package com.zhicore.content.application.service.query;

import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.dto.admin.outbox.OutboxFailedPageResponse;
import com.zhicore.content.application.model.OutboxEventRecord;
import com.zhicore.content.application.port.store.OutboxEventStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxAdminQueryServiceTest {

    @Mock
    private OutboxEventStore outboxEventStore;

    @InjectMocks
    private OutboxAdminQueryService outboxAdminQueryService;

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
