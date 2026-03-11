package com.zhicore.ranking.application.service;

import com.zhicore.api.client.PostBatchClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.ranking.domain.model.HotScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HotPostDetailService 测试")
class HotPostDetailServiceTest {

    @Mock
    private PostRankingQueryService postRankingService;

    @Mock
    private PostBatchClient postServiceClient;

    @InjectMocks
    private HotPostDetailService hotPostDetailService;

    @Test
    @DisplayName("文章服务降级时不应该伪造空榜单")
    void shouldThrowWhenPostServiceDegraded() {
        HotScore score = HotScore.builder().entityId("1001").score(12.3).rank(1).build();
        when(postRankingService.getHotPostsWithScore(0, 10)).thenReturn(List.of(score));
        when(postServiceClient.batchGetPosts(Set.of(1001L)))
                .thenReturn(ApiResponse.fail(ResultCode.SERVICE_DEGRADED, "文章服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> hotPostDetailService.getHotPostsWithDetails(0, 10));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("文章服务已降级", exception.getMessage());
    }

    @Test
    @DisplayName("文章详情获取成功时应该按排名顺序组装结果")
    void shouldAssembleHotPostsWhenRemoteCallSucceeds() {
        HotScore score = HotScore.builder().entityId("1001").score(12.3).rank(1).build();
        PostDTO post = new PostDTO();
        post.setId(1001L);
        post.setTitle("test");
        when(postRankingService.getHotPostsWithScore(0, 10)).thenReturn(List.of(score));
        when(postServiceClient.batchGetPosts(Set.of(1001L)))
                .thenReturn(ApiResponse.success(Map.of(1001L, post)));

        var result = hotPostDetailService.getHotPostsWithDetails(0, 10);

        assertEquals(1, result.size());
        assertEquals("1001", result.get(0).getId());
        assertEquals("test", result.get(0).getTitle());
    }
}
