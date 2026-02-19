package com.blog.idgenerator.service.impl;

import com.blog.common.exception.BusinessException;
import com.blog.idgenerator.service.IdGeneratorService;
import com.platform.idgen.client.IdGeneratorClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ID生成服务实现测试
 * 
 * 使用 Mock 测试 Service 层
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ID生成服务实现测试")
class IdGeneratorServiceImplTest {

    @Mock
    private IdGeneratorClient idGeneratorClient;

    @InjectMocks
    private IdGeneratorServiceImpl idGeneratorService;

    // ==================== Snowflake ID 生成测试 ====================

    @Test
    @DisplayName("应该成功生成单个Snowflake ID")
    void shouldGenerateSingleSnowflakeId() {
        // Given
        Long expectedId = 1234567890123456789L;
        when(idGeneratorClient.nextSnowflakeId()).thenReturn(expectedId);

        // When
        Long actualId = idGeneratorService.generateSnowflakeId();

        // Then
        assertThat(actualId).isEqualTo(expectedId);
        verify(idGeneratorClient, times(1)).nextSnowflakeId();
    }

    @Test
    @DisplayName("应该在ID生成抛出异常时包装为BusinessException")
    void shouldWrapExceptionAsBusinessException() {
        // Given
        RuntimeException cause = new RuntimeException("连接超时");
        when(idGeneratorClient.nextSnowflakeId()).thenThrow(cause);

        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateSnowflakeId())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID生成失败")
                .hasMessageContaining("连接超时")
                .hasCause(cause);
        
        verify(idGeneratorClient, times(1)).nextSnowflakeId();
    }

    // ==================== 批量 Snowflake ID 生成测试 ====================

    @Test
    @DisplayName("应该成功批量生成Snowflake ID")
    void shouldGenerateBatchSnowflakeIds() {
        // Given
        int count = 5;
        List<Long> expectedIds = Arrays.asList(
                1234567890123456789L,
                1234567890123456790L,
                1234567890123456791L,
                1234567890123456792L,
                1234567890123456793L
        );
        when(idGeneratorClient.nextSnowflakeIds(count)).thenReturn(expectedIds);

        // When
        List<Long> actualIds = idGeneratorService.generateBatchSnowflakeIds(count);

        // Then
        assertThat(actualIds).hasSize(count);
        assertThat(actualIds).isEqualTo(expectedIds);
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(count);
    }

    @Test
    @DisplayName("应该拒绝count为0的批量生成请求")
    void shouldRejectBatchCountZero() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生成数量必须在1-1000之间");
        
        verify(idGeneratorClient, never()).nextSnowflakeIds(anyInt());
    }

    @Test
    @DisplayName("应该拒绝count为负数的批量生成请求")
    void shouldRejectBatchCountNegative() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(-5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生成数量必须在1-1000之间");
        
        verify(idGeneratorClient, never()).nextSnowflakeIds(anyInt());
    }

    @Test
    @DisplayName("应该拒绝count超过1000的批量生成请求")
    void shouldRejectBatchCountTooLarge() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(1001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生成数量必须在1-1000之间");
        
        verify(idGeneratorClient, never()).nextSnowflakeIds(anyInt());
    }

    @Test
    @DisplayName("应该接受count为1的批量生成请求")
    void shouldAcceptBatchCountOne() {
        // Given
        List<Long> expectedIds = Collections.singletonList(1234567890123456789L);
        when(idGeneratorClient.nextSnowflakeIds(1)).thenReturn(expectedIds);

        // When
        List<Long> actualIds = idGeneratorService.generateBatchSnowflakeIds(1);

        // Then
        assertThat(actualIds).hasSize(1);
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(1);
    }

    @Test
    @DisplayName("应该接受count为1000的批量生成请求")
    void shouldAcceptBatchCountOneThousand() {
        // Given
        List<Long> expectedIds = Arrays.asList(new Long[1000]);
        when(idGeneratorClient.nextSnowflakeIds(1000)).thenReturn(expectedIds);

        // When
        List<Long> actualIds = idGeneratorService.generateBatchSnowflakeIds(1000);

        // Then
        assertThat(actualIds).hasSize(1000);
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(1000);
    }

    @Test
    @DisplayName("应该在批量生成返回null时抛出BusinessException")
    void shouldThrowBusinessExceptionWhenBatchIdsIsNull() {
        // Given
        int count = 5;
        when(idGeneratorClient.nextSnowflakeIds(count)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(count))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批量生成ID失败")
                .hasMessageContaining("返回值为空");
        
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(count);
    }

    @Test
    @DisplayName("应该在批量生成返回空列表时抛出BusinessException")
    void shouldThrowBusinessExceptionWhenBatchIdsIsEmpty() {
        // Given
        int count = 5;
        when(idGeneratorClient.nextSnowflakeIds(count)).thenReturn(Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(count))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批量生成ID失败")
                .hasMessageContaining("返回值为空");
        
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(count);
    }

    @Test
    @DisplayName("应该在批量生成返回数量不匹配时抛出BusinessException")
    void shouldThrowBusinessExceptionWhenBatchCountMismatch() {
        // Given
        int requestedCount = 5;
        List<Long> returnedIds = Arrays.asList(1L, 2L, 3L); // Only 3 IDs
        when(idGeneratorClient.nextSnowflakeIds(requestedCount)).thenReturn(returnedIds);

        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(requestedCount))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批量生成ID失败")
                .hasMessageContaining("返回数量不匹配")
                .hasMessageContaining("期望5个")
                .hasMessageContaining("实际3个");
        
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(requestedCount);
    }

    @Test
    @DisplayName("应该在批量生成抛出异常时包装为BusinessException")
    void shouldWrapBatchExceptionAsBusinessException() {
        // Given
        int count = 5;
        RuntimeException cause = new RuntimeException("服务不可用");
        when(idGeneratorClient.nextSnowflakeIds(count)).thenThrow(cause);

        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(count))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批量ID生成失败")
                .hasMessageContaining("服务不可用")
                .hasCause(cause);
        
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(count);
    }

    @Test
    @DisplayName("应该在批量生成参数异常时直接抛出IllegalArgumentException")
    void shouldNotWrapIllegalArgumentExceptionForBatch() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("生成数量必须在1-1000之间");
    }

    // ==================== Segment ID 生成测试 ====================

    @Test
    @DisplayName("应该成功生成Segment ID")
    void shouldGenerateSegmentId() {
        // Given
        String bizTag = "user";
        Long expectedId = 1000L;
        when(idGeneratorClient.nextSegmentId(bizTag)).thenReturn(expectedId);

        // When
        Long actualId = idGeneratorService.generateSegmentId(bizTag);

        // Then
        assertThat(actualId).isEqualTo(expectedId);
        verify(idGeneratorClient, times(1)).nextSegmentId(bizTag);
    }

    @Test
    @DisplayName("应该拒绝null的业务标签")
    void shouldRejectNullBizTag() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateSegmentId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("业务标签不能为空");
        
        verify(idGeneratorClient, never()).nextSegmentId(anyString());
    }

    @Test
    @DisplayName("应该拒绝空字符串的业务标签")
    void shouldRejectEmptyBizTag() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateSegmentId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("业务标签不能为空");
        
        verify(idGeneratorClient, never()).nextSegmentId(anyString());
    }

    @Test
    @DisplayName("应该拒绝仅包含空白字符的业务标签")
    void shouldRejectBlankBizTag() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateSegmentId("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("业务标签不能为空");
        
        verify(idGeneratorClient, never()).nextSegmentId(anyString());
    }

    @Test
    @DisplayName("应该接受有效的业务标签")
    void shouldAcceptValidBizTags() {
        // Given
        String[] validBizTags = {"user", "post", "comment", "order-123", "user_profile"};
        
        for (String bizTag : validBizTags) {
            Long expectedId = 1000L;
            when(idGeneratorClient.nextSegmentId(bizTag)).thenReturn(expectedId);

            // When
            Long actualId = idGeneratorService.generateSegmentId(bizTag);

            // Then
            assertThat(actualId).isEqualTo(expectedId);
        }
        
        verify(idGeneratorClient, times(validBizTags.length)).nextSegmentId(anyString());
    }

    @Test
    @DisplayName("应该在Segment ID生成抛出异常时包装为BusinessException")
    void shouldWrapSegmentExceptionAsBusinessException() {
        // Given
        String bizTag = "user";
        RuntimeException cause = new RuntimeException("数据库连接失败");
        when(idGeneratorClient.nextSegmentId(bizTag)).thenThrow(cause);

        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateSegmentId(bizTag))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ID生成失败")
                .hasMessageContaining("数据库连接失败")
                .hasCause(cause);
        
        verify(idGeneratorClient, times(1)).nextSegmentId(bizTag);
    }

    @Test
    @DisplayName("应该在Segment ID生成参数异常时直接抛出IllegalArgumentException")
    void shouldNotWrapIllegalArgumentExceptionForSegment() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateSegmentId(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("业务标签不能为空");
    }

    // ==================== 业务逻辑正确性测试 ====================

    @Test
    @DisplayName("应该正确调用IdGeneratorClient的方法")
    void shouldCallCorrectIdGeneratorClientMethods() {
        // Given
        Long snowflakeId = 1L;
        List<Long> batchIds = Arrays.asList(1L, 2L, 3L);
        Long segmentId = 100L;
        
        when(idGeneratorClient.nextSnowflakeId()).thenReturn(snowflakeId);
        when(idGeneratorClient.nextSnowflakeIds(3)).thenReturn(batchIds);
        when(idGeneratorClient.nextSegmentId("test")).thenReturn(segmentId);

        // When
        idGeneratorService.generateSnowflakeId();
        idGeneratorService.generateBatchSnowflakeIds(3);
        idGeneratorService.generateSegmentId("test");

        // Then
        verify(idGeneratorClient, times(1)).nextSnowflakeId();
        verify(idGeneratorClient, times(1)).nextSnowflakeIds(3);
        verify(idGeneratorClient, times(1)).nextSegmentId("test");
        verifyNoMoreInteractions(idGeneratorClient);
    }

    @Test
    @DisplayName("应该在参数验证失败时不调用IdGeneratorClient")
    void shouldNotCallIdGeneratorClientWhenValidationFails() {
        // When & Then
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> idGeneratorService.generateBatchSnowflakeIds(1001))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> idGeneratorService.generateSegmentId(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> idGeneratorService.generateSegmentId(""))
                .isInstanceOf(IllegalArgumentException.class);

        // Then
        verifyNoInteractions(idGeneratorClient);
    }
}
