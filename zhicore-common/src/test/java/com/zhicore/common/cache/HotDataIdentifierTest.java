package com.zhicore.common.cache;

import com.zhicore.common.config.CacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * HotDataIdentifier 单元测试
 * 
 * 测试热点数据识别器的核心功能：
 * 1. 访问记录功能
 * 2. 热点数据判断逻辑
 * 3. 手动标记功能
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("热点数据识别器单元测试")
class HotDataIdentifierTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private CacheProperties cacheProperties;
    private HotDataIdentifier hotDataIdentifier;

    @BeforeEach
    void setUp() {
        // 初始化配置
        cacheProperties = new CacheProperties();
        CacheProperties.HotData hotData = new CacheProperties.HotData();
        hotData.setEnabled(true);
        hotData.setThreshold(100);
        cacheProperties.setHotData(hotData);

        // 初始化被测试对象
        hotDataIdentifier = new HotDataIdentifier(redisTemplate, cacheProperties);

        // Mock RedisTemplate 的 opsForValue() 方法 (lenient 避免不必要的 stubbing 警告)
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== Section 1: 访问记录功能测试 ====================

    @Test
    @DisplayName("记录访问 - 正常场景")
    void recordAccess_withValidParameters_shouldIncrementCounter() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";

        // When
        hotDataIdentifier.recordAccess(entityType, entityId);

        // Then
        verify(valueOperations).increment(expectedKey);
        verify(redisTemplate).expire(expectedKey, 1, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("记录访问 - 空实体类型")
    void recordAccess_withNullEntityType_shouldNotRecordAccess() {
        // Given
        String entityType = null;
        Long entityId = 123L;

        // When
        hotDataIdentifier.recordAccess(entityType, entityId);

        // Then
        verify(valueOperations, never()).increment(anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("记录访问 - 空实体ID")
    void recordAccess_withNullEntityId_shouldNotRecordAccess() {
        // Given
        String entityType = "post";
        Long entityId = null;

        // When
        hotDataIdentifier.recordAccess(entityType, entityId);

        // Then
        verify(valueOperations, never()).increment(anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("记录访问 - 热点数据识别功能禁用")
    void recordAccess_whenHotDataDisabled_shouldNotRecordAccess() {
        // Given
        cacheProperties.getHotData().setEnabled(false);
        String entityType = "post";
        Long entityId = 123L;

        // When
        hotDataIdentifier.recordAccess(entityType, entityId);

        // Then
        verify(valueOperations, never()).increment(anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("记录访问 - Redis 异常不影响业务")
    void recordAccess_whenRedisThrowsException_shouldNotThrowException() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        when(valueOperations.increment(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When & Then
        assertThatCode(() -> hotDataIdentifier.recordAccess(entityType, entityId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("记录访问 - 不同实体类型")
    void recordAccess_withDifferentEntityTypes_shouldUseCorrectKeys() {
        // Given
        String[] entityTypes = {"post", "user", "comment"};
        Long entityId = 123L;

        // When
        for (String entityType : entityTypes) {
            hotDataIdentifier.recordAccess(entityType, entityId);
        }

        // Then
        verify(valueOperations).increment("hotdata:counter:post:123");
        verify(valueOperations).increment("hotdata:counter:user:123");
        verify(valueOperations).increment("hotdata:counter:comment:123");
    }

    // ==================== Section 2: 热点数据判断逻辑测试 ====================

    @Test
    @DisplayName("判断热点数据 - 访问次数超过阈值")
    void isHotData_whenAccessCountExceedsThreshold_shouldReturnTrue() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";
        when(valueOperations.get(expectedKey)).thenReturn(150);

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isTrue();
        verify(valueOperations).get(expectedKey);
    }

    @Test
    @DisplayName("判断热点数据 - 访问次数等于阈值")
    void isHotData_whenAccessCountEqualsThreshold_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";
        when(valueOperations.get(expectedKey)).thenReturn(100);

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("判断热点数据 - 访问次数低于阈值")
    void isHotData_whenAccessCountBelowThreshold_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";
        when(valueOperations.get(expectedKey)).thenReturn(50);

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("判断热点数据 - 计数器不存在")
    void isHotData_whenCounterDoesNotExist_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";
        when(valueOperations.get(expectedKey)).thenReturn(null);

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("判断热点数据 - 空实体类型")
    void isHotData_withNullEntityType_shouldReturnFalse() {
        // Given
        String entityType = null;
        Long entityId = 123L;

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isFalse();
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("判断热点数据 - 空实体ID")
    void isHotData_withNullEntityId_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = null;

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isFalse();
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("判断热点数据 - 热点数据识别功能禁用")
    void isHotData_whenHotDataDisabled_shouldReturnFalse() {
        // Given
        cacheProperties.getHotData().setEnabled(false);
        String entityType = "post";
        Long entityId = 123L;

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isFalse();
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("判断热点数据 - Redis 异常返回 false")
    void isHotData_whenRedisThrowsException_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("判断热点数据 - 自定义阈值")
    void isHotData_withCustomThreshold_shouldUseCustomThreshold() {
        // Given
        cacheProperties.getHotData().setThreshold(50);
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";
        when(valueOperations.get(expectedKey)).thenReturn(60);

        // When
        boolean result = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(result).isTrue();
    }

    // ==================== Section 3: 手动标记功能测试 ====================

    @Test
    @DisplayName("手动标记热点 - 正常场景")
    void markAsHot_withValidParameters_shouldMarkAsHot() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:manual:post:123";

        // When
        hotDataIdentifier.markAsHot(entityType, entityId);

        // Then
        verify(valueOperations).set(expectedKey, "1", 24, TimeUnit.HOURS);
    }

    @Test
    @DisplayName("手动标记热点 - 空实体类型")
    void markAsHot_withNullEntityType_shouldNotMark() {
        // Given
        String entityType = null;
        Long entityId = 123L;

        // When
        hotDataIdentifier.markAsHot(entityType, entityId);

        // Then
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("手动标记热点 - 空实体ID")
    void markAsHot_withNullEntityId_shouldNotMark() {
        // Given
        String entityType = "post";
        Long entityId = null;

        // When
        hotDataIdentifier.markAsHot(entityType, entityId);

        // Then
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("手动标记热点 - Redis 异常不抛出异常")
    void markAsHot_whenRedisThrowsException_shouldNotThrowException() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        doThrow(new RuntimeException("Redis error"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        // When & Then
        assertThatCode(() -> hotDataIdentifier.markAsHot(entityType, entityId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("检查手动标记 - 已标记")
    void isManuallyMarkedAsHot_whenMarked_shouldReturnTrue() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:manual:post:123";
        when(redisTemplate.hasKey(expectedKey)).thenReturn(true);

        // When
        boolean result = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);

        // Then
        assertThat(result).isTrue();
        verify(redisTemplate).hasKey(expectedKey);
    }

    @Test
    @DisplayName("检查手动标记 - 未标记")
    void isManuallyMarkedAsHot_whenNotMarked_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:manual:post:123";
        when(redisTemplate.hasKey(expectedKey)).thenReturn(false);

        // When
        boolean result = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("检查手动标记 - 空实体类型")
    void isManuallyMarkedAsHot_withNullEntityType_shouldReturnFalse() {
        // Given
        String entityType = null;
        Long entityId = 123L;

        // When
        boolean result = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);

        // Then
        assertThat(result).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("检查手动标记 - 空实体ID")
    void isManuallyMarkedAsHot_withNullEntityId_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = null;

        // When
        boolean result = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);

        // Then
        assertThat(result).isFalse();
        verify(redisTemplate, never()).hasKey(anyString());
    }

    @Test
    @DisplayName("检查手动标记 - Redis 异常返回 false")
    void isManuallyMarkedAsHot_whenRedisThrowsException_shouldReturnFalse() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        boolean result = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);

        // Then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("取消手动标记 - 正常场景")
    void unmarkAsHot_withValidParameters_shouldRemoveMark() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:manual:post:123";

        // When
        hotDataIdentifier.unmarkAsHot(entityType, entityId);

        // Then
        verify(redisTemplate).delete(expectedKey);
    }

    @Test
    @DisplayName("取消手动标记 - 空实体类型")
    void unmarkAsHot_withNullEntityType_shouldNotUnmark() {
        // Given
        String entityType = null;
        Long entityId = 123L;

        // When
        hotDataIdentifier.unmarkAsHot(entityType, entityId);

        // Then
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("取消手动标记 - 空实体ID")
    void unmarkAsHot_withNullEntityId_shouldNotUnmark() {
        // Given
        String entityType = "post";
        Long entityId = null;

        // When
        hotDataIdentifier.unmarkAsHot(entityType, entityId);

        // Then
        verify(redisTemplate, never()).delete(anyString());
    }

    // ==================== Section 4: 辅助功能测试 ====================

    @Test
    @DisplayName("获取访问计数 - 正常场景")
    void getAccessCount_withValidParameters_shouldReturnCount() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";
        when(valueOperations.get(expectedKey)).thenReturn(150);

        // When
        int result = hotDataIdentifier.getAccessCount(entityType, entityId);

        // Then
        assertThat(result).isEqualTo(150);
    }

    @Test
    @DisplayName("获取访问计数 - 计数器不存在")
    void getAccessCount_whenCounterDoesNotExist_shouldReturnZero() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";
        when(valueOperations.get(expectedKey)).thenReturn(null);

        // When
        int result = hotDataIdentifier.getAccessCount(entityType, entityId);

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("获取访问计数 - 空实体类型")
    void getAccessCount_withNullEntityType_shouldReturnZero() {
        // Given
        String entityType = null;
        Long entityId = 123L;

        // When
        int result = hotDataIdentifier.getAccessCount(entityType, entityId);

        // Then
        assertThat(result).isEqualTo(0);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    @DisplayName("获取访问计数 - Redis 异常返回 0")
    void getAccessCount_whenRedisThrowsException_shouldReturnZero() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When
        int result = hotDataIdentifier.getAccessCount(entityType, entityId);

        // Then
        assertThat(result).isEqualTo(0);
    }

    @Test
    @DisplayName("重置访问计数 - 正常场景")
    void resetAccessCount_withValidParameters_shouldDeleteCounter() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String expectedKey = "hotdata:counter:post:123";

        // When
        hotDataIdentifier.resetAccessCount(entityType, entityId);

        // Then
        verify(redisTemplate).delete(expectedKey);
    }

    @Test
    @DisplayName("重置访问计数 - 空实体类型")
    void resetAccessCount_withNullEntityType_shouldNotReset() {
        // Given
        String entityType = null;
        Long entityId = 123L;

        // When
        hotDataIdentifier.resetAccessCount(entityType, entityId);

        // Then
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("重置访问计数 - Redis 异常不抛出异常")
    void resetAccessCount_whenRedisThrowsException_shouldNotThrowException() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        when(redisTemplate.delete(anyString())).thenThrow(new RuntimeException("Redis error"));

        // When & Then
        assertThatCode(() -> hotDataIdentifier.resetAccessCount(entityType, entityId))
                .doesNotThrowAnyException();
    }

    // ==================== Section 5: 集成场景测试 ====================

    @Test
    @DisplayName("集成场景 - 记录访问后判断热点")
    void integrationScenario_recordAccessThenCheckHotData() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String counterKey = "hotdata:counter:post:123";
        
        // 模拟记录访问后的计数
        when(valueOperations.get(counterKey)).thenReturn(150);

        // When
        hotDataIdentifier.recordAccess(entityType, entityId);
        boolean isHot = hotDataIdentifier.isHotData(entityType, entityId);

        // Then
        assertThat(isHot).isTrue();
        verify(valueOperations).increment(counterKey);
        verify(valueOperations).get(counterKey);
    }

    @Test
    @DisplayName("集成场景 - 手动标记后检查")
    void integrationScenario_markAsHotThenCheck() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String manualKey = "hotdata:manual:post:123";
        when(redisTemplate.hasKey(manualKey)).thenReturn(true);

        // When
        hotDataIdentifier.markAsHot(entityType, entityId);
        boolean isMarked = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);

        // Then
        assertThat(isMarked).isTrue();
        verify(valueOperations).set(manualKey, "1", 24, TimeUnit.HOURS);
        verify(redisTemplate).hasKey(manualKey);
    }

    @Test
    @DisplayName("集成场景 - 标记后取消标记")
    void integrationScenario_markThenUnmark() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String manualKey = "hotdata:manual:post:123";
        
        // 第一次检查返回 true，取消标记后返回 false
        when(redisTemplate.hasKey(manualKey)).thenReturn(true, false);

        // When
        hotDataIdentifier.markAsHot(entityType, entityId);
        boolean isMarkedBefore = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);
        hotDataIdentifier.unmarkAsHot(entityType, entityId);
        boolean isMarkedAfter = hotDataIdentifier.isManuallyMarkedAsHot(entityType, entityId);

        // Then
        assertThat(isMarkedBefore).isTrue();
        assertThat(isMarkedAfter).isFalse();
        verify(redisTemplate).delete(manualKey);
    }

    @Test
    @DisplayName("集成场景 - 获取计数后重置")
    void integrationScenario_getCountThenReset() {
        // Given
        String entityType = "post";
        Long entityId = 123L;
        String counterKey = "hotdata:counter:post:123";
        
        // 第一次返回 150，重置后返回 null
        when(valueOperations.get(counterKey)).thenReturn(150, null);

        // When
        int countBefore = hotDataIdentifier.getAccessCount(entityType, entityId);
        hotDataIdentifier.resetAccessCount(entityType, entityId);
        int countAfter = hotDataIdentifier.getAccessCount(entityType, entityId);

        // Then
        assertThat(countBefore).isEqualTo(150);
        assertThat(countAfter).isEqualTo(0);
        verify(redisTemplate).delete(counterKey);
    }
}
