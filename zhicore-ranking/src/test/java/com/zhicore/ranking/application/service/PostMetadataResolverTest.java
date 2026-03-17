package com.zhicore.ranking.application.service;

import com.zhicore.api.client.PostBatchClient;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PostMetadataResolver 测试")
class PostMetadataResolverTest {

    @Mock
    private PostBatchClient postServiceClient;

    @InjectMocks
    private PostMetadataResolver postMetadataResolver;

    @Test
    @DisplayName("文章服务降级时应该抛出统一服务降级异常")
    void shouldThrowServiceDegradedWhenPostServiceFails() {
        when(postServiceClient.batchGetPosts(Set.of(1L)))
                .thenReturn(ApiResponse.fail(ResultCode.SERVICE_DEGRADED, "文章服务已降级"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> postMetadataResolver.resolve(Set.of(1L)));

        assertEquals(ResultCode.SERVICE_DEGRADED.getCode(), exception.getCode());
        assertEquals("文章服务已降级", exception.getMessage());
    }

    @Test
    @DisplayName("最佳努力解析在文章服务降级时应该返回空结果")
    void shouldReturnEmptyWhenResolveBestEffortFails() {
        when(postServiceClient.batchGetPosts(Set.of(1L)))
                .thenReturn(ApiResponse.fail(ResultCode.SERVICE_DEGRADED, "文章服务已降级"));

        assertTrue(postMetadataResolver.resolveBestEffort(Set.of(1L)).isEmpty());
    }
}
