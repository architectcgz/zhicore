package com.zhicore.ranking.infrastructure.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.zhicore.ranking.application.service.RankingLedgerIngestionService;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostViewedRankingConsumer Tests")
class PostViewedRankingConsumerTest {

    @Mock
    private RankingRedisRepository rankingRepository;

    @Mock
    private RankingLedgerIngestionService rankingLedgerIngestionService;

    @Test
    @DisplayName("onMessage should normalize instant timestamps before saving ledger event")
    void onMessageShouldNormalizeInstantTimestamps() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        PostViewedRankingConsumer consumer = new PostViewedRankingConsumer(
                rankingRepository,
                objectMapper,
                rankingLedgerIngestionService,
                new SimpleMeterRegistry()
        );

        Instant occurredAt = Instant.parse("2026-03-15T03:21:18Z");
        Instant publishedAt = Instant.parse("2026-03-15T02:21:18Z");
        String message = """
                {
                  "eventId":"evt-viewed-1",
                  "occurredAt":"2026-03-15T03:21:18Z",
                  "postId":1001,
                  "userId":2002,
                  "authorId":3003,
                  "publishedAt":"2026-03-15T02:21:18Z",
                  "clientIp":"127.0.0.1",
                  "userAgent":"JUnit"
                }
                """;

        when(rankingRepository.tryAcquireViewDedup("1001", "2002")).thenReturn(true);
        when(rankingRepository.incrementViewScoreWithCap("1001", 1.0, 5000.0)).thenReturn(1.0);
        when(rankingLedgerIngestionService.saveEvent(
                eq("evt-viewed-1"),
                eq("PostViewedEvent"),
                eq(1001L),
                eq(2002L),
                eq(3003L),
                eq(com.zhicore.ranking.domain.model.RankingMetricType.VIEW),
                eq(1),
                eq(LocalDateTime.ofInstant(occurredAt, ZoneId.systemDefault())),
                eq(LocalDateTime.ofInstant(publishedAt, ZoneId.systemDefault()))
        )).thenReturn(true);

        consumer.onMessage(message);

        verify(rankingLedgerIngestionService).saveEvent(
                eq("evt-viewed-1"),
                eq("PostViewedEvent"),
                eq(1001L),
                eq(2002L),
                eq(3003L),
                eq(com.zhicore.ranking.domain.model.RankingMetricType.VIEW),
                eq(1),
                eq(LocalDateTime.ofInstant(occurredAt, ZoneId.systemDefault())),
                eq(LocalDateTime.ofInstant(publishedAt, ZoneId.systemDefault()))
        );
    }
}
