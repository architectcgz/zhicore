package com.zhicore.ranking.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.exception.UnauthorizedException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.ranking.application.dto.HotPostCandidatesDTO;
import com.zhicore.ranking.application.dto.RankingReplayResultDTO;
import com.zhicore.ranking.application.service.HotPostDetailService;
import com.zhicore.ranking.application.service.RankingHotPostCandidateService;
import com.zhicore.ranking.application.service.RankingLedgerReplayService;
import com.zhicore.ranking.application.service.query.CreatorRankingQueryService;
import com.zhicore.ranking.application.service.query.PostRankingQueryService;
import com.zhicore.ranking.application.service.query.TopicRankingQueryService;
import com.zhicore.ranking.domain.model.HotScore;
import com.zhicore.ranking.infrastructure.config.RankingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingController Tests")
class RankingControllerTest {

    @Mock
    private PostRankingQueryService postRankingService;

    @Mock
    private HotPostDetailService hotPostDetailService;

    @Mock
    private CreatorRankingQueryService creatorRankingService;

    @Mock
    private TopicRankingQueryService topicRankingService;

    @Mock
    private RankingLedgerReplayService rankingLedgerReplayService;

    @Mock
    private RankingHotPostCandidateService rankingHotPostCandidateService;

    @Test
    @DisplayName("周榜接口应拒绝不存在的第 53 周")
    void weeklyEndpointShouldRejectInvalidWeek53() {
        RankingController controller = new RankingController(
                postRankingService,
                hotPostDetailService,
                creatorRankingService,
                topicRankingService,
                rankingLedgerReplayService,
                rankingHotPostCandidateService,
                rankingProperties()
        );

        ApiResponse<List<String>> response = controller.getWeeklyHotPosts(2025, 53, 20);

        assertEquals(400, response.getCode());
        assertEquals("周参数无效，该年份不存在这一周", response.getMessage());
        verify(postRankingService, never()).getWeeklyHotPosts(2025, 53, 20);
    }

    @Test
    @DisplayName("周榜带分数接口应接受合法的第 53 周")
    void weeklyScoresEndpointShouldAcceptValidWeek53() {
        RankingController controller = new RankingController(
                postRankingService,
                hotPostDetailService,
                creatorRankingService,
                topicRankingService,
                rankingLedgerReplayService,
                rankingHotPostCandidateService,
                rankingProperties()
        );
        when(postRankingService.getWeeklyHotPostsWithScore(2020, 53, 20))
                .thenReturn(List.of(HotScore.ofWithRank("1001", 10.0, 1)));

        ApiResponse<List<HotScore>> response = controller.getWeeklyHotPostsWithScore(2020, 53, 20);

        assertEquals(200, response.getCode());
        assertTrue(response.getData().stream().anyMatch(score -> "1001".equals(score.getEntityId())));
        verify(postRankingService).getWeeklyHotPostsWithScore(2020, 53, 20);
    }

    @Test
    @DisplayName("ledger 补算管理接口应返回补算结果")
    void rebuildFromLedgerEndpointShouldReturnReplayResult() {
        RankingController controller = new RankingController(
                postRankingService,
                hotPostDetailService,
                creatorRankingService,
                topicRankingService,
                rankingLedgerReplayService,
                rankingHotPostCandidateService,
                rankingProperties()
        );
        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1L);
            userContext.when(UserContext::isAdmin).thenReturn(true);
            when(rankingLedgerReplayService.rebuildFromLedger()).thenReturn(42);

            ApiResponse<RankingReplayResultDTO> response = controller.rebuildFromLedger();

            assertEquals(200, response.getCode());
            assertEquals(42, response.getData().getReplayedEvents());
            verify(rankingLedgerReplayService).rebuildFromLedger();
        }
    }

    @Test
    @DisplayName("ledger 补算管理接口应拒绝未登录用户")
    void rebuildFromLedgerEndpointShouldRejectAnonymousUser() {
        RankingController controller = new RankingController(
                postRankingService,
                hotPostDetailService,
                creatorRankingService,
                topicRankingService,
                rankingLedgerReplayService,
                rankingHotPostCandidateService,
                rankingProperties()
        );
        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            assertThrows(UnauthorizedException.class, controller::rebuildFromLedger);
            verify(rankingLedgerReplayService, never()).rebuildFromLedger();
        }
    }

    @Test
    @DisplayName("ledger 补算管理接口应拒绝非管理员")
    void rebuildFromLedgerEndpointShouldRejectNonAdminUser() {
        RankingController controller = new RankingController(
                postRankingService,
                hotPostDetailService,
                creatorRankingService,
                topicRankingService,
                rankingLedgerReplayService,
                rankingHotPostCandidateService,
                rankingProperties()
        );
        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);
            userContext.when(UserContext::isAdmin).thenReturn(false);

            assertThrows(ForbiddenException.class, controller::rebuildFromLedger);
            verify(rankingLedgerReplayService, never()).rebuildFromLedger();
        }
    }

    @Test
    @DisplayName("热门候选集接口应返回候选快照")
    void hotCandidatesEndpointShouldReturnSnapshot() {
        RankingController controller = new RankingController(
                postRankingService,
                hotPostDetailService,
                creatorRankingService,
                topicRankingService,
                rankingLedgerReplayService,
                rankingHotPostCandidateService,
                rankingProperties()
        );
        HotPostCandidatesDTO candidates = HotPostCandidatesDTO.builder()
                .version("v1")
                .candidateSize(2)
                .stale(false)
                .build();
        when(rankingHotPostCandidateService.getCandidates()).thenReturn(candidates);

        ApiResponse<HotPostCandidatesDTO> response = controller.getHotPostCandidates();

        assertEquals(200, response.getCode());
        assertEquals("v1", response.getData().getVersion());
        verify(rankingHotPostCandidateService).getCandidates();
    }

    private RankingProperties rankingProperties() {
        RankingProperties properties = new RankingProperties();
        properties.setDefaultSize(20);
        properties.setMaxSize(100);
        return properties;
    }
}
