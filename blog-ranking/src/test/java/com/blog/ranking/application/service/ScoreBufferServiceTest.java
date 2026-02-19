package com.blog.ranking.application.service;

import com.blog.ranking.infrastructure.redis.RankingRedisRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ScoreBufferService 单元测试
 *
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
class ScoreBufferServiceTest {

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    @InjectMocks
    private ScoreBufferService scoreBufferService;

    @BeforeEach
    void setUp() {
        // 设置配置值
        ReflectionTestUtils.setField(scoreBufferService, "flushInterval", 5000L);
        ReflectionTestUtils.setField(scoreBufferService, "batchSize", 1000);
    }

    @Test
    void testAddScore_ShouldAccumulateScores() {
        // Given
        String entityType = "post";
        String entityId = "123456";
        double delta1 = 10.0;
        double delta2 = 5.0;

        // When
        scoreBufferService.addScore(entityType, entityId, delta1);
        scoreBufferService.addScore(entityType, entityId, delta2);

        // Then
        assertEquals(1, scoreBufferService.getBufferSize());
    }

    @Test
    void testAddScore_DifferentEntities_ShouldCreateSeparateBuffers() {
        // Given
        String entityType1 = "post";
        String entityId1 = "123456";
        String entityType2 = "creator";
        String entityId2 = "789012";

        // When
        scoreBufferService.addScore(entityType1, entityId1, 10.0);
        scoreBufferService.addScore(entityType2, entityId2, 5.0);

        // Then
        assertEquals(2, scoreBufferService.getBufferSize());
    }

    @Test
    void testFlushToRedis_EmptyBuffer_ShouldNotCallRepository() {
        // Given
        scoreBufferService.clearBuffer();

        // When
        scoreBufferService.flushToRedis();

        // Then
        verifyNoInteractions(rankingRedisRepository);
    }

    @Test
    void testFlushToRedis_WithPostScores_ShouldCallIncrementPostScore() {
        // Given
        String entityType = "post";
        String entityId = "123456";
        double delta = 15.0;
        scoreBufferService.addScore(entityType, entityId, delta);

        // When
        scoreBufferService.flushToRedis();

        // Then
        verify(rankingRedisRepository, times(1)).incrementPostScore(entityId, delta);
        assertEquals(1, scoreBufferService.getBufferSize()); // Key still exists but value is 0
    }

    @Test
    void testFlushToRedis_WithCreatorScores_ShouldCallIncrementCreatorScore() {
        // Given
        String entityType = "creator";
        String entityId = "789012";
        double delta = 20.0;
        scoreBufferService.addScore(entityType, entityId, delta);

        // When
        scoreBufferService.flushToRedis();

        // Then
        verify(rankingRedisRepository, times(1)).incrementCreatorScore(entityId, delta);
    }

    @Test
    void testFlushToRedis_WithTopicScores_ShouldCallIncrementTopicScore() {
        // Given
        String entityType = "topic";
        String entityId = "345678";
        double delta = 25.0;
        scoreBufferService.addScore(entityType, entityId, delta);

        // When
        scoreBufferService.flushToRedis();

        // Then
        verify(rankingRedisRepository, times(1)).incrementTopicScore(Long.parseLong(entityId), delta);
    }

    @Test
    void testFlushToRedis_WithMultipleScores_ShouldFlushAll() {
        // Given
        scoreBufferService.addScore("post", "111", 10.0);
        scoreBufferService.addScore("creator", "222", 20.0);
        scoreBufferService.addScore("topic", "333", 30.0);

        // When
        scoreBufferService.flushToRedis();

        // Then
        verify(rankingRedisRepository, times(1)).incrementPostScore("111", 10.0);
        verify(rankingRedisRepository, times(1)).incrementCreatorScore("222", 20.0);
        verify(rankingRedisRepository, times(1)).incrementTopicScore(333L, 30.0);
    }

    @Test
    void testFlushToRedis_WithInvalidKey_ShouldLogWarning() {
        // Given - 手动添加无效格式的 key
        ReflectionTestUtils.invokeMethod(scoreBufferService, "addScore", "invalid_key_format", "", 10.0);

        // When
        scoreBufferService.flushToRedis();

        // Then - 不应该抛出异常，只记录警告日志
        verifyNoInteractions(rankingRedisRepository);
    }

    @Test
    void testFlushToRedis_WithUnknownEntityType_ShouldLogWarning() {
        // Given
        String entityType = "unknown";
        String entityId = "123";
        scoreBufferService.addScore(entityType, entityId, 10.0);

        // When
        scoreBufferService.flushToRedis();

        // Then - 不应该调用任何 repository 方法
        verifyNoInteractions(rankingRedisRepository);
    }

    @Test
    void testFlushToRedis_WithException_ShouldNotThrow() {
        // Given
        scoreBufferService.addScore("post", "123", 10.0);
        doThrow(new RuntimeException("Redis connection failed"))
                .when(rankingRedisRepository).incrementPostScore(anyString(), anyDouble());

        // When & Then - 不应该抛出异常
        assertDoesNotThrow(() -> scoreBufferService.flushToRedis());
    }

    @Test
    void testShutdown_ShouldFlushRemainingData() {
        // Given
        scoreBufferService.addScore("post", "123", 10.0);

        // When
        scoreBufferService.shutdown();

        // Then
        verify(rankingRedisRepository, times(1)).incrementPostScore("123", 10.0);
    }

    @Test
    void testClearBuffer_ShouldRemoveAllEntries() {
        // Given
        scoreBufferService.addScore("post", "111", 10.0);
        scoreBufferService.addScore("creator", "222", 20.0);
        assertEquals(2, scoreBufferService.getBufferSize());

        // When
        scoreBufferService.clearBuffer();

        // Then
        assertEquals(0, scoreBufferService.getBufferSize());
    }

    @Test
    void testConcurrentAddScore_ShouldBeThreadSafe() throws InterruptedException {
        // Given
        String entityType = "post";
        String entityId = "123";
        int threadCount = 10;
        int incrementsPerThread = 100;
        double deltaPerIncrement = 1.0;

        // When - 多线程并发添加分数
        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    scoreBufferService.addScore(entityType, entityId, deltaPerIncrement);
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // Then - 刷写并验证总分数
        scoreBufferService.flushToRedis();
        
        // 验证调用了一次，且总分数正确
        double expectedTotal = threadCount * incrementsPerThread * deltaPerIncrement;
        verify(rankingRedisRepository, times(1)).incrementPostScore(entityId, expectedTotal);
    }
}
