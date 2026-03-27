package com.zhicore.notification.infrastructure.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisNotificationUnreadCountStore 测试")
class RedisNotificationUnreadCountStoreTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("increment 应通过单次原子脚本执行，命中返回 true")
    void increment_ShouldUseSingleAtomicScriptWhenHit() {
        RedisNotificationUnreadCountStore store = new RedisNotificationUnreadCountStore(redisTemplate);
        String key = NotificationRedisKeys.unreadCount("11");
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)));

        boolean updated = store.increment(11L);

        assertTrue(updated);
        ArgumentCaptor<RedisScript> scriptCaptor = ArgumentCaptor.forClass(RedisScript.class);
        verify(redisTemplate).execute(scriptCaptor.capture(), eq(List.of(key)));
        String script = ((DefaultRedisScript<?>) scriptCaptor.getValue()).getScriptAsString();
        assertTrue(script.contains("EXISTS"));
        assertTrue(script.contains("INCRBY"));
        verify(redisTemplate, never()).hasKey(anyString());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("increment 命中失败时应返回 false 且不创建脏 key")
    void increment_ShouldReturnFalseWhenMissWithoutDirtyKey() {
        RedisNotificationUnreadCountStore store = new RedisNotificationUnreadCountStore(redisTemplate);
        String key = NotificationRedisKeys.unreadCount("11");
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)));

        boolean updated = store.increment(11L);

        assertFalse(updated);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)));
        verify(redisTemplate, never()).hasKey(anyString());
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("decrement 在非法 delta 下应直接返回 false")
    void decrement_ShouldRejectInvalidDelta() {
        RedisNotificationUnreadCountStore store = new RedisNotificationUnreadCountStore(redisTemplate);

        assertFalse(store.decrement(11L, 0));
        assertFalse(store.decrement(11L, -1));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("decrement 应通过单次原子脚本执行，命中返回 true")
    void decrement_ShouldUseSingleAtomicScriptWhenHit() {
        RedisNotificationUnreadCountStore store = new RedisNotificationUnreadCountStore(redisTemplate);
        String key = NotificationRedisKeys.unreadCount("11");
        doReturn(1L).when(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), eq(3));

        boolean updated = store.decrement(11L, 3);

        assertTrue(updated);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), eq(3));
        verify(redisTemplate, never()).hasKey(anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("decrement 命中失败时应返回 false 且不创建脏 key")
    void decrement_ShouldReturnFalseWhenMissWithoutDirtyKey() {
        RedisNotificationUnreadCountStore store = new RedisNotificationUnreadCountStore(redisTemplate);
        String key = NotificationRedisKeys.unreadCount("11");
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), eq(2));

        boolean updated = store.decrement(11L, 2);

        assertFalse(updated);
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of(key)), eq(2));
        verify(redisTemplate, never()).hasKey(anyString());
        verify(redisTemplate, never()).delete(anyString());
    }
}
