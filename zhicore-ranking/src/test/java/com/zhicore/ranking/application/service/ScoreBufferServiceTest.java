package com.zhicore.ranking.application.service;

import com.zhicore.ranking.infrastructure.config.RankingBufferProperties;
import com.zhicore.ranking.infrastructure.redis.RankingRedisRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ScoreBufferService 单元测试
 *
 * 测试范围：
 * 1. 分数累加与缓冲区管理
 * 2. AtomicReference swap-and-flush 原子替换
 * 3. 刷写失败补偿（merge 回缓冲区）
 * 4. 事件计数器与阈值触发
 * 5. 优雅停机（stopAccepting）
 * 6. 并发安全
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScoreBufferService Tests")
class ScoreBufferServiceTest {

    @Mock
    private RankingRedisRepository rankingRedisRepository;

    private RankingBufferProperties bufferProperties;
    private ScoreBufferService scoreBufferService;

    @BeforeEach
    void setUp() {
        bufferProperties = new RankingBufferProperties();
        bufferProperties.setFlushInterval(5000L);
        bufferProperties.setBatchSize(Integer.MAX_VALUE);
        scoreBufferService = new ScoreBufferService(
                rankingRedisRepository, bufferProperties, new SimpleMeterRegistry());
    }

    // ==================== 分数累加测试 ====================

    @Test
    @DisplayName("添加分数 - 同一实体累加到同一缓冲区条目")
    void testAddScore_ShouldAccumulateScores() {
        scoreBufferService.addScore("post", "123456", 10.0);
        scoreBufferService.addScore("post", "123456", 5.0);
        assertEquals(1, scoreBufferService.getBufferSize());
        assertEquals(2, scoreBufferService.getEventCount());
    }

    @Test
    @DisplayName("添加分数 - 不同实体创建独立缓冲区条目")
    void testAddScore_DifferentEntities_ShouldCreateSeparateBuffers() {
        scoreBufferService.addScore("post", "123456", 10.0);
        scoreBufferService.addScore("creator", "789012", 5.0);
        assertEquals(2, scoreBufferService.getBufferSize());
    }

    // ==================== flush 测试 ====================

    @Test
    @DisplayName("flush - 空缓冲区不调用 Repository")
    void testFlush_EmptyBuffer_ShouldNotCallRepository() {
        scoreBufferService.clearBuffer();
        int flushed = scoreBufferService.flush();
        assertEquals(0, flushed);
        verifyNoInteractions(rankingRedisRepository);
    }

    @Test
    @DisplayName("flush - 文章分数正确刷写到 Redis")
    void testFlush_WithPostScores_ShouldCallIncrementPostScore() {
        scoreBufferService.addScore("post", "123456", 15.0);
        int flushed = scoreBufferService.flush();
        assertEquals(1, flushed);
        verify(rankingRedisRepository, times(1)).incrementPostScore("123456", 15.0);
        // swap 后缓冲区应该为空
        assertEquals(0, scoreBufferService.getBufferSize());
    }

    @Test
    @DisplayName("flush - 创作者分数正确刷写")
    void testFlush_WithCreatorScores() {
        scoreBufferService.addScore("creator", "789012", 20.0);
        scoreBufferService.flush();
        verify(rankingRedisRepository, times(1)).incrementCreatorScore("789012", 20.0);
    }

    @Test
    @DisplayName("flush - 话题分数正确刷写")
    void testFlush_WithTopicScores() {
        scoreBufferService.addScore("topic", "345678", 25.0);
        scoreBufferService.flush();
        verify(rankingRedisRepository, times(1)).incrementTopicScore(345678L, 25.0);
    }

    @Test
    @DisplayName("flush - 多种实体类型全部刷写")
    void testFlush_WithMultipleScores_ShouldFlushAll() {
        scoreBufferService.addScore("post", "111", 10.0);
        scoreBufferService.addScore("creator", "222", 20.0);
        scoreBufferService.addScore("topic", "333", 30.0);

        int flushed = scoreBufferService.flush();
        assertEquals(3, flushed);
        verify(rankingRedisRepository).incrementPostScore("111", 10.0);
        verify(rankingRedisRepository).incrementCreatorScore("222", 20.0);
        verify(rankingRedisRepository).incrementTopicScore(333L, 30.0);
    }

    // ==================== swap-and-flush 原子性测试 ====================

    @Test
    @DisplayName("flush 后缓冲区被原子替换为空 Map")
    void testFlush_SwapCreatesNewBuffer() {
        scoreBufferService.addScore("post", "111", 10.0);
        assertEquals(1, scoreBufferService.getBufferSize());

        scoreBufferService.flush();

        // swap 后旧 Map 已被替换，新 Map 为空
        assertEquals(0, scoreBufferService.getBufferSize());
        assertEquals(0, scoreBufferService.getEventCount());
    }

    @Test
    @DisplayName("flush 期间新事件写入新 Map，不影响当前刷写")
    void testFlush_NewEventsGoToNewBuffer() {
        scoreBufferService.addScore("post", "111", 10.0);
        scoreBufferService.flush();

        // flush 后添加新事件
        scoreBufferService.addScore("post", "222", 20.0);
        assertEquals(1, scoreBufferService.getBufferSize());

        // 再次 flush 应该只刷写新事件
        scoreBufferService.flush();
        verify(rankingRedisRepository).incrementPostScore("222", 20.0);
    }

    // ==================== 刷写失败补偿测试 ====================

    @Test
    @DisplayName("刷写失败时将失败条目 merge 回缓冲区")
    void testFlush_FailedEntries_MergedBackToBuffer() {
        scoreBufferService.addScore("post", "fail-post", 10.0);
        doThrow(new RuntimeException("Redis connection failed"))
                .when(rankingRedisRepository).incrementPostScore(eq("fail-post"), anyDouble());

        int flushed = scoreBufferService.flush();
        assertEquals(0, flushed);

        // 失败的条目应该被 merge 回缓冲区
        assertEquals(1, scoreBufferService.getBufferSize());
    }

    @Test
    @DisplayName("部分刷写失败时，成功的不回填，失败的回填")
    void testFlush_PartialFailure() {
        scoreBufferService.addScore("post", "ok-post", 10.0);
        scoreBufferService.addScore("post", "fail-post", 20.0);

        doNothing().when(rankingRedisRepository).incrementPostScore(eq("ok-post"), anyDouble());
        doThrow(new RuntimeException("fail"))
                .when(rankingRedisRepository).incrementPostScore(eq("fail-post"), anyDouble());

        scoreBufferService.flush();

        // ok-post 成功刷写，fail-post 回填到缓冲区
        verify(rankingRedisRepository).incrementPostScore("ok-post", 10.0);
        assertEquals(1, scoreBufferService.getBufferSize());
    }

    // ==================== 未知实体类型测试 ====================

    @Test
    @DisplayName("未知实体类型不调用 Repository")
    void testFlush_WithUnknownEntityType() {
        scoreBufferService.addScore("unknown", "123", 10.0);
        scoreBufferService.flush();
        verifyNoInteractions(rankingRedisRepository);
    }

    // ==================== 优雅停机测试 ====================

    @Test
    @DisplayName("stopAccepting 后不再接收新事件")
    void testStopAccepting_RejectsNewEvents() {
        scoreBufferService.stopAccepting();
        scoreBufferService.addScore("post", "123", 10.0);
        assertEquals(0, scoreBufferService.getBufferSize());
        assertTrue(scoreBufferService.isStopped());
    }

    @Test
    @DisplayName("stopAccepting 后仍可 flush 已有数据")
    void testStopAccepting_CanStillFlush() {
        scoreBufferService.addScore("post", "123", 10.0);
        scoreBufferService.stopAccepting();

        int flushed = scoreBufferService.flush();
        assertEquals(1, flushed);
        verify(rankingRedisRepository).incrementPostScore("123", 10.0);
    }

    // ==================== 事件计数器测试 ====================

    @Test
    @DisplayName("事件计数器正确递增")
    void testEventCounter_Increments() {
        scoreBufferService.addScore("post", "111", 1.0);
        scoreBufferService.addScore("post", "111", 1.0);
        scoreBufferService.addScore("post", "222", 1.0);
        assertEquals(3, scoreBufferService.getEventCount());
    }

    @Test
    @DisplayName("flush 后事件计数器重置为 0")
    void testEventCounter_ResetAfterFlush() {
        scoreBufferService.addScore("post", "111", 1.0);
        scoreBufferService.addScore("post", "222", 1.0);
        assertEquals(2, scoreBufferService.getEventCount());

        scoreBufferService.flush();
        assertEquals(0, scoreBufferService.getEventCount());
    }

    // ==================== clearBuffer 测试 ====================

    @Test
    @DisplayName("clearBuffer 清空所有条目和计数器")
    void testClearBuffer() {
        scoreBufferService.addScore("post", "111", 10.0);
        scoreBufferService.addScore("creator", "222", 20.0);
        assertEquals(2, scoreBufferService.getBufferSize());

        scoreBufferService.clearBuffer();
        assertEquals(0, scoreBufferService.getBufferSize());
        assertEquals(0, scoreBufferService.getEventCount());
    }

    // ==================== 并发安全测试 ====================

    @Test
    @DisplayName("多线程并发添加分数 - 线程安全")
    void testConcurrentAddScore_ShouldBeThreadSafe() throws InterruptedException {
        int threadCount = 10;
        int incrementsPerThread = 100;
        double deltaPerIncrement = 1.0;

        Thread[] threads = new Thread[threadCount];
        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    scoreBufferService.addScore("post", "123", deltaPerIncrement);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // 事件计数器应该等于总事件数
        assertEquals(threadCount * incrementsPerThread, scoreBufferService.getEventCount());

        scoreBufferService.flush();
        double expectedTotal = threadCount * incrementsPerThread * deltaPerIncrement;
        verify(rankingRedisRepository, times(1)).incrementPostScore("123", expectedTotal);
    }
}
