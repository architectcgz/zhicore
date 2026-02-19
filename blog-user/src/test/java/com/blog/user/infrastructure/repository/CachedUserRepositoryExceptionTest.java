package com.blog.user.infrastructure.repository;

import com.blog.common.cache.CacheConstants;
import com.blog.common.cache.HotDataIdentifier;
import com.blog.common.config.CacheProperties;
import com.blog.user.domain.model.User;
import com.blog.user.domain.repository.UserRepository;
import com.blog.user.infrastructure.cache.UserRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户服务缓存击穿防护异常处理测试
 * 
 * 测试场景：
 * 1. Redis 连接失败场景
 * 2. 锁释放失败场景
 * 3. 数据库查询失败场景
 * 
 * Requirements: 8.1, 8.2, 8.3, 8.4
 * 
 * @author Blog Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CachedUserRepository 异常处理测试")
class CachedUserRepositoryExceptionTest {

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
    private UserRepository delegate;

    private CacheProperties cacheProperties;
    private CachedUserRepository cachedRepository;

    private static final Long TEST_USER_ID = 1L;
    private static final String ENTITY_TYPE_USER = "user";

    @BeforeEach
    void setUp() {
        // 配置 RedisTemplate mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 配置 CacheProperties
        cacheProperties = new CacheProperties();
        cacheProperties.getLock().setWaitTime(5L);
        cacheProperties.getLock().setLeaseTime(10L);
        cacheProperties.getTtl().setEntityDetail(600L);
        cacheProperties.getTtl().setNullValue(60L);

        // 创建被测试对象
        cachedRepository = new CachedUserRepository(
                redisTemplate,
                cacheProperties,
                redissonClient,
                hotDataIdentifier,
                delegate
        );
    }

    // ==================== Redis 连接失败场景测试 ====================

    @Test
    @DisplayName("测试 Redis 连接失败 - findById 降级查询数据库")
    void testFindById_RedisConnectionFailed_FallbackToDatabase() {
        // Given: Redis 连接失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RedisConnectionFailureException("Unable to connect to Redis"));
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 降级查询数据库，返回正确结果
        assertTrue(result.isPresent(), "Should return database result when Redis fails");
        assertEquals(dbUser.getId(), result.get().getId());
        assertEquals(dbUser.getUserName(), result.get().getUserName());
        verify(delegate, times(1)).findById(TEST_USER_ID);
        verify(hotDataIdentifier, never()).recordAccess(any(), any());
    }

    @Test
    @DisplayName("测试 Redis 超时异常 - 降级查询数据库")
    void testFindById_RedisTimeout_FallbackToDatabase() {
        // Given: Redis 操作超时
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RuntimeException("Redis operation timeout"));
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 降级查询数据库
        assertTrue(result.isPresent());
        assertEquals(dbUser.getId(), result.get().getId());
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("测试 Redis 网络异常 - 降级查询数据库")
    void testFindById_RedisNetworkError_FallbackToDatabase() {
        // Given: Redis 网络异常
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RuntimeException("Network error: Connection reset"));
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 降级查询数据库
        assertTrue(result.isPresent());
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("测试 Redis 序列化异常 - 降级查询数据库")
    void testFindById_RedisSerializationError_FallbackToDatabase() {
        // Given: Redis 序列化异常
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RuntimeException("Serialization error: Cannot deserialize User"));
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 降级查询数据库
        assertTrue(result.isPresent());
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("测试 Redis 连接失败 - 用户不存在返回 empty")
    void testFindById_RedisConnectionFailed_UserNotFound_ReturnsEmpty() {
        // Given: Redis 连接失败，用户不存在
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RedisConnectionFailureException("Redis failed"));
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.empty());

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 返回 empty
        assertFalse(result.isPresent(), "Should return empty when user not found");
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    // ==================== 锁释放失败场景测试 ====================

    @Test
    @DisplayName("测试锁释放失败 - 不影响业务流程返回正确结果")
    void testFindById_LockReleaseFailed_ReturnsCorrectResult() throws InterruptedException {
        // Given: 锁释放失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        String lockKey = UserRedisKeys.lockDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));
        doThrow(new RuntimeException("Lock release failed: Redis connection lost"))
                .when(lock).unlock();

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 正常返回结果，锁释放失败不影响业务
        assertTrue(result.isPresent(), "Should return result even if lock release fails");
        assertEquals(dbUser.getId(), result.get().getId());
        assertEquals(dbUser.getUserName(), result.get().getUserName());
        verify(lock, times(1)).unlock();
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("测试锁释放网络异常 - 依赖自动过期机制")
    void testFindById_LockReleaseNetworkError_ReliesOnAutoExpiration() throws InterruptedException {
        // Given: 锁释放时网络异常
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        String lockKey = UserRedisKeys.lockDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));
        doThrow(new RedisConnectionFailureException("Network error during unlock"))
                .when(lock).unlock();

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 正常返回结果，依赖锁的自动过期机制
        assertTrue(result.isPresent());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试锁释放失败 - 用户不存在返回 empty")
    void testFindById_LockReleaseFailed_UserNotFound_ReturnsEmpty() throws InterruptedException {
        // Given: 锁释放失败，用户不存在
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        String lockKey = UserRedisKeys.lockDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Lock release failed"))
                .when(lock).unlock();

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 正常返回 empty
        assertFalse(result.isPresent());
        verify(lock, times(1)).unlock();
    }

    // ==================== 数据库查询失败场景测试 ====================

    @Test
    @DisplayName("测试数据库查询失败 - 锁被正确释放")
    void testFindById_DatabaseQueryFailed_LockReleased() throws InterruptedException {
        // Given: 数据库查询失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        String lockKey = UserRedisKeys.lockDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_USER_ID))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then: 调用 findById 抛出异常
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> cachedRepository.findById(TEST_USER_ID));
        
        assertEquals("Database connection failed", exception.getMessage());
        
        // 验证锁被释放
        verify(lock, times(1)).unlock();
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("测试数据库超时异常 - 锁被正确释放")
    void testFindById_DatabaseTimeout_LockReleased() throws InterruptedException {
        // Given: 数据库查询超时
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        String lockKey = UserRedisKeys.lockDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_USER_ID))
                .thenThrow(new RuntimeException("Query timeout: 30 seconds exceeded"));

        // When & Then: 抛出异常
        assertThrows(RuntimeException.class, 
                () -> cachedRepository.findById(TEST_USER_ID));
        
        // 验证锁被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试数据库查询失败 - 非热点数据不使用锁")
    void testFindById_DatabaseQueryFailed_NonHotData_NoLock() {
        // Given: 非热点数据，数据库查询失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(false);
        when(delegate.findById(TEST_USER_ID))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then: 抛出异常
        assertThrows(RuntimeException.class, 
                () -> cachedRepository.findById(TEST_USER_ID));
        
        // 验证没有使用锁
        verify(redissonClient, never()).getLock(any());
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    // ==================== 缓存写入失败场景测试 ====================

    @Test
    @DisplayName("测试缓存写入失败 - 不影响业务流程")
    void testFindById_CacheWriteFailed_ReturnsCorrectResult() {
        // Given: 缓存写入失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(false);
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));
        doThrow(new RuntimeException("Cache write failed: Redis out of memory"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 正常返回结果，缓存写入失败不影响业务
        assertTrue(result.isPresent(), "Should return result even if cache write fails");
        assertEquals(dbUser.getId(), result.get().getId());
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    @Test
    @DisplayName("测试缓存写入失败 - 热点数据锁被正确释放")
    void testFindById_CacheWriteFailed_HotData_LockReleased() throws InterruptedException {
        // Given: 热点数据，缓存写入失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        String lockKey = UserRedisKeys.lockDetail(TEST_USER_ID);
        User dbUser = createTestUser();
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.of(dbUser));
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(anyString(), any(), anyLong(), any());

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 正常返回结果，锁被释放
        assertTrue(result.isPresent());
        assertEquals(dbUser.getId(), result.get().getId());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试空值缓存写入失败 - 不影响业务流程")
    void testFindById_NullValueCacheWriteFailed_ReturnsEmpty() {
        // Given: 空值缓存写入失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(false);
        when(delegate.findById(TEST_USER_ID)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("Cache write failed"))
                .when(valueOperations).set(eq(cacheKey), eq(CacheConstants.NULL_VALUE), anyLong(), any());

        // When: 调用 findById
        Optional<User> result = cachedRepository.findById(TEST_USER_ID);

        // Then: 正常返回 empty
        assertFalse(result.isPresent(), "Should return empty even if null value cache write fails");
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    // ==================== 复合异常场景测试 ====================

    @Test
    @DisplayName("测试数据库查询失败且锁释放失败 - 抛出数据库异常")
    void testFindById_DatabaseAndLockReleaseFailed_ThrowsDatabaseException() throws InterruptedException {
        // Given: 数据库查询失败，锁释放也失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        String lockKey = UserRedisKeys.lockDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey)).thenReturn(null).thenReturn(null);
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, TEST_USER_ID)).thenReturn(true);
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(5L, 10L, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(delegate.findById(TEST_USER_ID))
                .thenThrow(new RuntimeException("Database failed"));
        doThrow(new RuntimeException("Lock release failed"))
                .when(lock).unlock();

        // When & Then: 抛出数据库异常（主要异常）
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> cachedRepository.findById(TEST_USER_ID));
        
        assertEquals("Database failed", exception.getMessage());
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试 Redis 连接失败且数据库查询失败 - 抛出数据库异常")
    void testFindById_RedisAndDatabaseFailed_ThrowsDatabaseException() {
        // Given: Redis 连接失败，数据库查询也失败
        String cacheKey = UserRedisKeys.userDetail(TEST_USER_ID);
        
        when(valueOperations.get(cacheKey))
                .thenThrow(new RedisConnectionFailureException("Redis failed"));
        when(delegate.findById(TEST_USER_ID))
                .thenThrow(new RuntimeException("Database failed"));

        // When & Then: 抛出数据库异常
        RuntimeException exception = assertThrows(RuntimeException.class, 
                () -> cachedRepository.findById(TEST_USER_ID));
        
        assertEquals("Database failed", exception.getMessage());
        verify(delegate, times(1)).findById(TEST_USER_ID);
    }

    // ==================== 辅助方法 ====================

    private User createTestUser() {
        return User.reconstitute(
            TEST_USER_ID,
            "testuser",
            "testuser",
            "test@example.com",
            "hashedpassword",
            null,
            null,
            com.blog.user.domain.model.UserStatus.ACTIVE,
            false,
            new java.util.HashSet<>(),
            java.time.LocalDateTime.now(),
            java.time.LocalDateTime.now()
        );
    }
}
