package com.zhicore.content.infrastructure.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedissonLockManagerImplTask05Test {

    @Test
    void unlock_whenUnlockThrows_incrementsCounter() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        RedissonClient redissonClient = mock(RedissonClient.class);
        RLock lock = mock(RLock.class);

        when(redissonClient.getLock("k")).thenReturn(lock);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        doThrow(new RuntimeException("boom")).when(lock).unlock();

        RedissonLockManagerImpl lockManager = new RedissonLockManagerImpl(redissonClient, meterRegistry);
        lockManager.unlock("k");

        double count = meterRegistry
                .get("content.lock.release.failure")
                .tag("component", "redisson")
                .counter()
                .count();

        assertThat(count).isEqualTo(1.0);
    }
}

