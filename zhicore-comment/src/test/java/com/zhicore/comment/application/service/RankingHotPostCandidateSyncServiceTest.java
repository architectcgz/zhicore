package com.zhicore.comment.application.service;

import com.zhicore.comment.application.dto.RankingHotPostCandidateItem;
import com.zhicore.comment.application.dto.RankingHotPostCandidatesResponse;
import com.zhicore.comment.application.dto.RankingHotPostCandidateMetadata;
import com.zhicore.comment.application.port.store.RankingHotPostCandidateStore;
import com.zhicore.comment.infrastructure.feign.RankingServiceClient;
import com.zhicore.common.result.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingHotPostCandidateSyncService Tests")
class RankingHotPostCandidateSyncServiceTest {

    @Mock
    private RankingServiceClient rankingServiceClient;

    @Mock
    private RankingHotPostCandidateStore rankingHotPostCandidateStore;

    @Test
    @DisplayName("同步成功时应覆盖本地候选集并更新元信息")
    void shouldReplaceCandidatesWhenSyncSucceeded() {
        RankingHotPostCandidatesResponse response = new RankingHotPostCandidatesResponse(
                "v1",
                Instant.now(),
                3,
                false,
                List.of(
                        new RankingHotPostCandidateItem("1001", 1, 99.9),
                        new RankingHotPostCandidateItem("1002", 2, 88.8),
                        new RankingHotPostCandidateItem("1002", 3, 77.7)
                )
        );
        when(rankingServiceClient.getHotPostCandidates()).thenReturn(ApiResponse.success(response));
        RankingHotPostCandidateSyncService service = new RankingHotPostCandidateSyncService(
                rankingServiceClient,
                rankingHotPostCandidateStore
        );

        service.refreshCandidates();

        ArgumentCaptor<Set<Long>> setCaptor = ArgumentCaptor.forClass(Set.class);
        ArgumentCaptor<RankingHotPostCandidateMetadata> metadataCaptor =
                ArgumentCaptor.forClass(RankingHotPostCandidateMetadata.class);
        verify(rankingHotPostCandidateStore).replaceCandidates(setCaptor.capture(), metadataCaptor.capture());
        assertThat(setCaptor.getValue()).containsExactlyInAnyOrder(1001L, 1002L);
        assertThat(metadataCaptor.getValue().candidateCount()).isEqualTo(2);
        assertThat(metadataCaptor.getValue().syncedAt()).isNotNull();
    }

    @Test
    @DisplayName("同步失败时应保留旧候选集")
    void shouldKeepOldCandidatesWhenSyncFailed() {
        when(rankingServiceClient.getHotPostCandidates()).thenReturn(ApiResponse.fail("degraded"));
        RankingHotPostCandidateSyncService service = new RankingHotPostCandidateSyncService(
                rankingServiceClient,
                rankingHotPostCandidateStore
        );

        service.refreshCandidates();

        verify(rankingHotPostCandidateStore, never()).replaceCandidates(org.mockito.ArgumentMatchers.anySet(), org.mockito.ArgumentMatchers.any());
    }
}
