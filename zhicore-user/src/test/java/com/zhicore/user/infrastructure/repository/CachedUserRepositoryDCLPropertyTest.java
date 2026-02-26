package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 2: Double-Checked Locking (DCL) Correctness
 * Validates: Requirements 1.2, 2.2, 3.2
 * 
 * Property Test: Validates Double-Checked Locking (DCL) correctness in user service
 * 
 * Test Property:
 * For any userId, when a request successfully acquires the lock, if the cache has been filled by another thread,
 * the request should read data directly from cache without querying the database.
 * 
 * @author ZhiCore Team
 */
@DisplayName("Property 2: User Service Double-Checked Locking (DCL) Correctness Property Test")
class CachedUserRepositoryDCLPropertyTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private CacheProperties.Lock lockProperties;

    @Mock
    private CacheProperties.Ttl ttlProperties;

    @Mock
    private UserRepository delegate;

    private CachedUserRepository cachedRepository;

    private static final String ENTITY_TYPE_USER = "user";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Configure RedisTemplate mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Configure CacheProperties mock
        when(cacheProperties.getLock()).thenReturn(lockProperties);
        when(cacheProperties.getTtl()).thenReturn(ttlProperties);
        when(lockProperties.getWaitTime()).thenReturn(5L);
        when(lockProperties.getLeaseTime()).thenReturn(10L);
        when(ttlProperties.getEntityDetail()).thenReturn(600L);

        // Create test object
        cachedRepository = new CachedUserRepository(
                redisTemplate,
                cacheProperties,
                redissonClient,
                hotDataIdentifier,
                delegate
        );
    }

    /**
     * Property 2: Double-Checked Locking (DCL) Correctness
     * 
     * For any randomly generated userId, when a request successfully acquires the lock, if the cache has been filled by another thread,
     * the request should read data directly from cache without querying the database.
     */
    @Property(tries = 100)
    @DisplayName("Property 2: After acquiring lock, if cache is filled, no database query")
    void testDCL_CacheFilledByOtherThread_NoDatabaseQuery(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId) throws Exception {
        
        // Given: Configure cache behavior - first miss, second hit (DCL)
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        User cachedUser = createTestUser(userId);
        
        // Simulate cache behavior: first check miss, second check hit (DCL)
        AtomicInteger cacheCheckCount = new AtomicInteger(0);
        when(valueOperations.get(cacheKey)).thenAnswer(invocation -> {
            int count = cacheCheckCount.incrementAndGet();
            if (count == 1) {
                // First check: cache miss
                return null;
            } else {
                // Second check (DCL): cache filled by another thread
                return cachedUser;
            }
        });
        
        // Mark as hot data
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_USER, userId)).thenReturn(false);
        
        // Configure lock behavior: can acquire lock successfully
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // Simulate database query (should not be called)
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        when(delegate.findById(userId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            return Optional.of(createTestUser(userId));
        });
        
        // When: Call query method
        Optional<User> result = cachedRepository.findById(userId);
        
        // Then: Verify result
        assertTrue(result.isPresent(), "Should return data from cache");
        assertEquals(cachedUser.getId(), result.get().getId(), "Returned data should be from cache");
        
        // Core verification: database should not be queried
        assertEquals(0, databaseQueryCount.get(),
                String.format("Database should not be queried, actual query count: %d", databaseQueryCount.get()));
        
        // Verify: cache checked twice (first + DCL)
        assertEquals(2, cacheCheckCount.get(),
                String.format("Cache should be checked twice (first + DCL), actual check count: %d", cacheCheckCount.get()));
        
        // Verify: database query method not called
        verify(delegate, never()).findById(userId);
        
        // Verify: lock released correctly
        verify(lock, times(1)).unlock();
    }

    /**
     * Property 2 Variant: DCL correctness in multi-threaded scenario
     */
    @Property(tries = 100)
    @DisplayName("Property 2 Variant: DCL avoids duplicate queries in multi-threaded scenario")
    void testDCL_MultipleThreads_OnlyFirstQueriesDatabase(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId) throws Exception {
        
        // Given: Configure cache and lock behavior
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        User dbUser = createTestUser(userId);
        
        // Simulate cache behavior
        AtomicInteger cacheCheckCount = new AtomicInteger(0);
        AtomicInteger cacheFillCount = new AtomicInteger(0);
        
        when(valueOperations.get(cacheKey)).thenAnswer(invocation -> {
            int count = cacheCheckCount.incrementAndGet();
            if (count <= 2) {
                return null;
            } else {
                return dbUser;
            }
        });
        
        doAnswer(invocation -> {
            cacheFillCount.incrementAndGet();
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // Simulate database query
        AtomicInteger databaseQueryCount = new AtomicInteger(0);
        when(delegate.findById(userId)).thenAnswer(invocation -> {
            databaseQueryCount.incrementAndGet();
            Thread.sleep(10);
            return Optional.of(dbUser);
        });
        
        // When: Start multiple concurrent threads
        int concurrentThreads = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(concurrentThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        
        for (int i = 0; i < concurrentThreads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    Optional<User> result = cachedRepository.findById(userId);
                    if (result.isPresent()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        
        // Then: Verify result
        assertTrue(completed, "All threads should complete within 5 seconds");
        
        // Core verification: only first thread queries database
        assertEquals(1, databaseQueryCount.get(),
                String.format("Only first thread should query database, actual query count: %d", databaseQueryCount.get()));
        
        // Verify: all requests succeed
        assertEquals(concurrentThreads, successCount.get(),
                String.format("All requests should succeed, actual success count: %d", successCount.get()));
        
        // Verify: cache filled only once
        assertEquals(1, cacheFillCount.get(),
                String.format("Cache should be filled only once, actual fill count: %d", cacheFillCount.get()));
        
        // Verify: database queried only once
        verify(delegate, times(1)).findById(userId);
    }

    /**
     * Property 2 Variant: Verify no lock logic when cache hit
     */
    @Property(tries = 100)
    @DisplayName("Property 2 Variant: No lock acquisition when cache hit")
    void testDCL_CacheHit_NoLockAcquisition(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId) throws Exception {
        
        // Given: Configure cache hit
        String cacheKey = UserRedisKeys.userDetail(userId);
        User cachedUser = createTestUser(userId);
        
        when(valueOperations.get(cacheKey)).thenReturn(cachedUser);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        
        // When: Call query method
        Optional<User> result = cachedRepository.findById(userId);
        
        // Then: Verify result
        assertTrue(result.isPresent(), "Should return data from cache");
        assertEquals(cachedUser.getId(), result.get().getId(), "Returned data should be from cache");
        
        // Core verification: should not attempt to acquire lock
        verify(redissonClient, never()).getLock(anyString());
        
        // Verify: should not query database
        verify(delegate, never()).findById(userId);
        
        // Verify: cache checked only once
        verify(valueOperations, times(1)).get(cacheKey);
    }

    // ==================== Helper Methods ====================

    private User createTestUser(Long userId) {
        return User.reconstitute(
                userId,
                "testuser" + userId,
                "testuser" + userId,
                "test" + userId + "@example.com",
                "hashedpassword",
                null,
                null,
                UserStatus.ACTIVE,
                false,
                new java.util.HashSet<>(),
                0L,
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
