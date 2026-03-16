package com.zhicore.ranking.application.service;

import com.zhicore.ranking.application.model.RankingBucketRecord;
import com.zhicore.ranking.application.model.RankingPostStateRecord;
import com.zhicore.ranking.domain.service.HotScoreCalculator;
import com.zhicore.ranking.infrastructure.config.RankingPipelineProperties;
import com.zhicore.ranking.infrastructure.config.RankingWeightProperties;
import com.zhicore.ranking.infrastructure.pg.PgRankingLedgerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingLedgerFlushService Tests")
class RankingLedgerFlushServiceTest {

    @Mock
    private PgRankingLedgerRepository repository;

    @Mock
    private PostMetadataResolver postMetadataResolver;

    @Mock
    private RankingRedisMaterializationService rankingRedisMaterializationService;

    private RankingLedgerFlushService service;

    @BeforeEach
    void setUp() {
        RankingPipelineProperties pipelineProperties = new RankingPipelineProperties();
        pipelineProperties.setBucketWindowSeconds(10);
        pipelineProperties.setFlushInterval(5000L);
        pipelineProperties.setFlushBatchSize(200);

        RankingWeightProperties weightProperties = new RankingWeightProperties();
        weightProperties.setView(1.0);
        weightProperties.setLike(5.0);
        weightProperties.setComment(10.0);
        weightProperties.setFavorite(8.0);
        weightProperties.setHalfLifeDays(7.0);

        service = new RankingLedgerFlushService(
                repository,
                postMetadataResolver,
                new HotScoreCalculator(weightProperties),
                pipelineProperties,
                rankingRedisMaterializationService
        );
    }

    @Test
    @DisplayName("flushPendingBuckets should claim buckets and insert initial post state with version 0")
    void flushPendingBucketsShouldInsertInitialState() {
        LocalDateTime bucketStart = LocalDateTime.of(2026, 3, 14, 10, 0);
        RankingBucketRecord bucket = RankingBucketRecord.builder()
                .bucketStart(bucketStart)
                .postId(1001L)
                .viewDelta(3)
                .likeDelta(1)
                .build();
        when(repository.claimFlushableBuckets(anyInt(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(bucket));
        when(repository.findPostStatesByIds(Collections.singleton(1001L))).thenReturn(Collections.emptyMap());
        when(postMetadataResolver.resolveBestEffort(Collections.singleton(1001L))).thenReturn(Map.of(
                1001L,
                PostMetadataResolver.PostMetadata.builder()
                        .postId(1001L)
                        .authorId(2001L)
                        .publishedAt(LocalDateTime.of(2026, 3, 1, 8, 0))
                        .topicIds(List.of(3001L))
                        .build()
        ));

        int flushed = service.flushPendingBuckets();

        assertEquals(1, flushed);
        ArgumentCaptor<RankingPostStateRecord> stateCaptor = ArgumentCaptor.forClass(RankingPostStateRecord.class);
        verify(repository).savePostState(stateCaptor.capture());
        RankingPostStateRecord savedState = stateCaptor.getValue();
        assertEquals(1001L, savedState.getPostId());
        assertEquals(2001L, savedState.getAuthorId());
        assertEquals(0L, savedState.getVersion());
        verify(repository).markBucketFlushed(eq(bucket), anyString(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("flushPendingBuckets should bump post_state version when updating existing state")
    void flushPendingBucketsShouldBumpStateVersion() {
        LocalDateTime bucketStart = LocalDateTime.of(2026, 3, 14, 10, 0);
        RankingBucketRecord bucket = RankingBucketRecord.builder()
                .bucketStart(bucketStart)
                .postId(1001L)
                .commentDelta(1)
                .build();
        RankingPostStateRecord existingState = RankingPostStateRecord.builder()
                .postId(1001L)
                .authorId(2001L)
                .publishedAt(LocalDateTime.of(2026, 3, 1, 8, 0))
                .topicIds(List.of(3001L))
                .viewCount(10L)
                .likeCount(2)
                .favoriteCount(1)
                .commentCount(0)
                .rawScore(28D)
                .hotScore(28D)
                .version(3L)
                .lastBucketStart(LocalDateTime.of(2026, 3, 14, 9, 50))
                .updatedAt(LocalDateTime.of(2026, 3, 14, 9, 50))
                .build();

        when(repository.claimFlushableBuckets(anyInt(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(bucket));
        when(repository.findPostStatesByIds(Collections.singleton(1001L))).thenReturn(Map.of(1001L, existingState));

        int flushed = service.flushPendingBuckets();

        assertEquals(1, flushed);
        ArgumentCaptor<RankingPostStateRecord> stateCaptor = ArgumentCaptor.forClass(RankingPostStateRecord.class);
        verify(repository).savePostState(stateCaptor.capture());
        assertEquals(4L, stateCaptor.getValue().getVersion());
    }

    @Test
    @DisplayName("flushPendingBuckets should allow missing metadata when resolver degrades")
    void flushPendingBucketsShouldAllowMissingMetadata() {
        LocalDateTime bucketStart = LocalDateTime.of(2026, 3, 14, 10, 0);
        RankingBucketRecord bucket = RankingBucketRecord.builder()
                .bucketStart(bucketStart)
                .postId(1002L)
                .viewDelta(5)
                .build();

        when(repository.claimFlushableBuckets(anyInt(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(bucket));
        when(repository.findPostStatesByIds(Collections.singleton(1002L))).thenReturn(Collections.emptyMap());
        when(postMetadataResolver.resolveBestEffort(Collections.singleton(1002L))).thenReturn(Collections.emptyMap());

        int flushed = service.flushPendingBuckets();

        assertEquals(1, flushed);
        ArgumentCaptor<RankingPostStateRecord> stateCaptor = ArgumentCaptor.forClass(RankingPostStateRecord.class);
        verify(repository).savePostState(stateCaptor.capture());
        RankingPostStateRecord savedState = stateCaptor.getValue();
        assertEquals(1002L, savedState.getPostId());
        assertNull(savedState.getAuthorId());
        assertNull(savedState.getPublishedAt());
        assertEquals(List.of(), savedState.getTopicIds());
    }

    @Test
    @DisplayName("flushPendingBuckets should leave one bucket window for late arrivals before claiming")
    void flushPendingBucketsShouldDelayClaimUpperBound() {
        when(repository.claimFlushableBuckets(anyInt(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of());

        LocalDateTime before = LocalDateTime.now();
        int flushed = service.flushPendingBuckets();
        LocalDateTime after = LocalDateTime.now();

        assertEquals(0, flushed);
        ArgumentCaptor<LocalDateTime> upperBoundCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(repository).claimFlushableBuckets(
                anyInt(),
                anyString(),
                any(LocalDateTime.class),
                upperBoundCaptor.capture(),
                any(LocalDateTime.class)
        );

        LocalDateTime upperBound = upperBoundCaptor.getValue();
        long minDelaySeconds = Math.min(
                ChronoUnit.SECONDS.between(upperBound, before),
                ChronoUnit.SECONDS.between(upperBound, after)
        );
        assertEquals(0, upperBound.getSecond() % 10);
        org.junit.jupiter.api.Assertions.assertTrue(minDelaySeconds >= 10,
                "flush upper bound should lag behind now by at least one bucket window");
    }

    @Test
    @DisplayName("flushPendingBuckets should only materialize late arrivals once for an already flushed bucket")
    void flushPendingBucketsShouldOnlyApplyPendingDelta() {
        LocalDateTime bucketStart = LocalDateTime.of(2026, 3, 14, 10, 0);
        RankingBucketRecord bucket = RankingBucketRecord.builder()
                .bucketStart(bucketStart)
                .postId(1003L)
                .viewDelta(20)
                .likeDelta(20)
                .appliedViewDelta(19)
                .appliedLikeDelta(20)
                .build();
        RankingPostStateRecord existingState = RankingPostStateRecord.builder()
                .postId(1003L)
                .authorId(2003L)
                .publishedAt(LocalDateTime.now().minusHours(1))
                .topicIds(List.of(3003L))
                .viewCount(19L)
                .likeCount(20)
                .favoriteCount(0)
                .commentCount(0)
                .rawScore(119D)
                .hotScore(119D)
                .version(7L)
                .lastBucketStart(bucketStart)
                .updatedAt(LocalDateTime.of(2026, 3, 14, 10, 1))
                .build();

        when(repository.claimFlushableBuckets(anyInt(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(bucket));
        when(repository.findPostStatesByIds(Collections.singleton(1003L))).thenReturn(Map.of(1003L, existingState));

        int flushed = service.flushPendingBuckets();

        assertEquals(1, flushed);
        ArgumentCaptor<RankingPostStateRecord> stateCaptor = ArgumentCaptor.forClass(RankingPostStateRecord.class);
        ArgumentCaptor<Double> periodDeltaCaptor = ArgumentCaptor.forClass(Double.class);
        verify(repository).savePostState(stateCaptor.capture());
        verify(repository).incrementPeriodScore(eq("DAY"), eq("2026-03-14"), eq(1003L), periodDeltaCaptor.capture(), any(LocalDateTime.class));
        verify(repository).markBucketFlushed(eq(bucket), anyString(), any(LocalDateTime.class));

        RankingPostStateRecord savedState = stateCaptor.getValue();
        assertEquals(20L, savedState.getViewCount());
        assertEquals(20, savedState.getLikeCount());
        assertEquals(120D, savedState.getRawScore());
        assertEquals(8L, savedState.getVersion());
        assertEquals(1D, periodDeltaCaptor.getValue());
        assertTrue(bucket.toPendingBucket().hasPendingDelta());
        assertEquals(1L, bucket.toPendingBucket().getViewDelta());
        assertEquals(0, bucket.toPendingBucket().getLikeDelta());
    }

    @Test
    @DisplayName("flushPendingBuckets should apply negative pending delta when a bucket receives a compensating delete")
    void flushPendingBucketsShouldApplyNegativePendingDelta() {
        LocalDateTime bucketStart = LocalDateTime.of(2026, 3, 14, 10, 0);
        RankingBucketRecord bucket = RankingBucketRecord.builder()
                .bucketStart(bucketStart)
                .postId(1004L)
                .commentDelta(0)
                .appliedCommentDelta(1)
                .build();
        RankingPostStateRecord existingState = RankingPostStateRecord.builder()
                .postId(1004L)
                .authorId(2004L)
                .publishedAt(LocalDateTime.now().minusHours(2))
                .topicIds(List.of(3004L))
                .viewCount(0L)
                .likeCount(0)
                .favoriteCount(0)
                .commentCount(10)
                .rawScore(100D)
                .hotScore(100D)
                .version(5L)
                .lastBucketStart(bucketStart)
                .updatedAt(LocalDateTime.of(2026, 3, 14, 10, 1))
                .build();

        when(repository.claimFlushableBuckets(anyInt(), anyString(), any(LocalDateTime.class), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(bucket));
        when(repository.findPostStatesByIds(Collections.singleton(1004L))).thenReturn(Map.of(1004L, existingState));

        int flushed = service.flushPendingBuckets();

        assertEquals(1, flushed);
        ArgumentCaptor<RankingPostStateRecord> stateCaptor = ArgumentCaptor.forClass(RankingPostStateRecord.class);
        verify(repository).savePostState(stateCaptor.capture());
        RankingPostStateRecord savedState = stateCaptor.getValue();
        assertEquals(9, savedState.getCommentCount());
        assertEquals(90D, savedState.getRawScore());
        assertEquals(6L, savedState.getVersion());
        assertTrue(bucket.toPendingBucket().hasPendingDelta());
        assertEquals(-1, bucket.toPendingBucket().getCommentDelta());
    }
}
