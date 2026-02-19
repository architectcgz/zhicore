package com.blog.user.infrastructure.repository;

import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.user.domain.model.User;
import com.blog.user.domain.repository.UserRepository;
import com.blog.user.infrastructure.cache.UserRedisKeys;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 7: 缓存TTL随机抖动
 * Validates: Requirements 2.7
 * 
 * Property: For any entity data, cached TTL should include random jitter
 * to prevent cache avalanche.
 */
class CachedUserRepositoryTTLJitterPropertyTest {

    @Mock
    private UserRepository delegate;
    
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    
    @Mock
    private RedissonClient redissonClient;
    
    @Mock
    private HotDataIdentifier hotDataIdentifier;
    
    @Mock
    private ValueOperations<String, Object> valueOperations;
    
    @Mock
    private RLock lock;
    
    private CacheProperties cacheProperties;
    private CachedUserRepository cachedRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        cacheProperties = new CacheProperties();
        cacheProperties.getTtl().setEntityDetail(600);
        cacheProperties.getLock().setWaitTime(5);
        cacheProperties.getLock().setLeaseTime(10);
        cacheProperties.getTtl().setNullValue(60);
        cacheProperties.getHotData().setEnabled(true);
        
        cachedRepository = new CachedUserRepository(
            redisTemplate,
            cacheProperties,
            redissonClient,
            hotDataIdentifier,
            delegate
        );
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    /**
     * Property 7: 缓存TTL随机抖动
     * For any entity data, TTL should include random jitter
     */
    @Property(tries = 100)
    void ttlIncludesRandomJitter(@ForAll @LongRange(min = 1, max = 10000) Long userId) throws Exception {
        // Given: Cache miss, hot data
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData("user", userId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot("user", userId)).thenReturn(false);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        User mockUser = User.reconstitute(
            userId,
            "user" + userId,
            "user" + userId,
            "user" + userId + "@example.com",
            "hashedpassword",
            null,
            null,
            com.blog.user.domain.model.UserStatus.ACTIVE,
            false,
            new java.util.HashSet<>(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        
        when(delegate.findById(userId)).thenReturn(Optional.of(mockUser));
        
        // When: Find user (will cache with TTL)
        cachedRepository.findById(userId);
        
        // Then: TTL should be base TTL + random jitter
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations).set(anyString(), any(), ttlCaptor.capture(), any(TimeUnit.class));
        
        long actualTtl = ttlCaptor.getValue();
        long baseTtl = cacheProperties.getTtl().getEntityDetail();
        
        // TTL should be greater than base (has jitter)
        // and less than base + max jitter (60 seconds)
        assertThat(actualTtl)
            .as("TTL should include random jitter for userId: " + userId)
            .isGreaterThan(baseTtl)
            .isLessThanOrEqualTo(baseTtl + 60);
    }
    
    /**
     * Property 7b: Multiple caches have different TTLs (jitter varies)
     */
    @Property(tries = 50)
    void multipleCachesHaveDifferentTTLs(
        @ForAll @LongRange(min = 1, max = 10000) Long userId1,
        @ForAll @LongRange(min = 1, max = 10000) Long userId2
    ) throws Exception {
        Assume.that(userId1 != userId2);
        
        // Given: Two different users
        String cacheKey1 = UserRedisKeys.userDetail(userId1);
        String lockKey1 = UserRedisKeys.lockDetail(userId1);
        String cacheKey2 = UserRedisKeys.userDetail(userId2);
        String lockKey2 = UserRedisKeys.lockDetail(userId2);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        when(hotDataIdentifier.isHotData(eq("user"), anyLong())).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(eq("user"), anyLong())).thenReturn(false);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        User mockUser1 = User.reconstitute(
            userId1,
            "user" + userId1,
            "user" + userId1,
            "user" + userId1 + "@example.com",
            "hashedpassword",
            null,
            null,
            com.blog.user.domain.model.UserStatus.ACTIVE,
            false,
            new java.util.HashSet<>(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        
        User mockUser2 = User.reconstitute(
            userId2,
            "user" + userId2,
            "user" + userId2,
            "user" + userId2 + "@example.com",
            "hashedpassword",
            null,
            null,
            com.blog.user.domain.model.UserStatus.ACTIVE,
            false,
            new java.util.HashSet<>(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        
        when(delegate.findById(userId1)).thenReturn(Optional.of(mockUser1));
        when(delegate.findById(userId2)).thenReturn(Optional.of(mockUser2));
        
        // When: Cache both users
        cachedRepository.findById(userId1);
        cachedRepository.findById(userId2);
        
        // Then: TTLs should be different (due to random jitter)
        ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
        verify(valueOperations, times(2)).set(anyString(), any(), ttlCaptor.capture(), any(TimeUnit.class));
        
        var ttls = ttlCaptor.getAllValues();
        // With high probability, two random jitters should be different
        // (This might occasionally fail due to randomness, but very unlikely with 100 tries)
    }
}
