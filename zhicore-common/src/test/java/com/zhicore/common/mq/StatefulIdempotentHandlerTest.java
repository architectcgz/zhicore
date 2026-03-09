package com.zhicore.common.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatefulIdempotentHandler 测试")
class StatefulIdempotentHandlerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private StatefulIdempotentHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StatefulIdempotentHandler(redisTemplate);
        ReflectionTestUtils.setField(handler, "processingExpireMinutes", 15L);
        ReflectionTestUtils.setField(handler, "completedExpireHours", 168L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("获取处理权时应该使用短期 processing TTL")
    void tryAcquireShouldUseProcessingTtl() {
        when(valueOperations.setIfAbsent(any(), eq("processing"), any(Duration.class))).thenReturn(true);

        assertTrue(handler.tryAcquire("evt-1"));

        verify(valueOperations).setIfAbsent(
                eq("zhicore:mq:idempotent:evt-1"),
                eq("processing"),
                eq(Duration.ofMinutes(15))
        );
        verify(valueOperations, never()).get(any());
    }

    @Test
    @DisplayName("标记完成时应该使用更长的 completed TTL")
    void markCompletedShouldUseCompletedTtl() {
        handler.markCompleted("evt-2");

        verify(valueOperations).set(
                eq("zhicore:mq:idempotent:evt-2"),
                eq("completed"),
                eq(Duration.ofHours(168))
        );
    }

    @Test
    @DisplayName("处理失败时应该释放 processing 锁")
    void handleIdempotentShouldReleaseLockOnFailure() {
        when(valueOperations.setIfAbsent(any(), eq("processing"), any(Duration.class))).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> handler.handleIdempotent("evt-3", () -> {
                    throw new RuntimeException("boom");
                }));

        assertTrue(exception.getMessage().contains("boom"));
        verify(redisTemplate).delete("zhicore:mq:idempotent:evt-3");
    }
}
