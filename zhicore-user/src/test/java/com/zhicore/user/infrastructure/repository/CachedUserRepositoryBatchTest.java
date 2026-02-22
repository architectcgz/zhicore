package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 批量查询优化单元测试
 * 
 * 测试场景：
 * 1. 全部缓存命中场景
 * 2. 部分缓存命中场景
 * 3. 全部缓存未命中场景
 * 4. 死锁避免机制（ID排序）
 * 
 * Requirements: 12.1, 12.2, 12.3, 12.4
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CachedUserRepository Batch Query Tests")
class CachedUserRepositoryBatchTest {

    @Mock
    private UserRepository delegate;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private HotDataIdentifier hotDataIdentifier;

    @Mock
    private CacheProperties cacheProperties;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private RLock lock;

    @InjectMocks
    private CachedUserRepository cachedUserRepository;

    private User testUser1;
    private User testUser2;
    private User testUser3;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Mock nested configuration objects
        CacheProperties.Lock lockConfig = new CacheProperties.Lock();
        lockConfig.setWaitTime(5L);
        lockConfig.setLeaseTime(10L);
        lenient().when(cacheProperties.getLock()).thenReturn(lockConfig);
        
        CacheProperties.Ttl ttlConfig = new CacheProperties.Ttl();
        ttlConfig.setNullValue(60L);
        ttlConfig.setEntityDetail(600L);
        lenient().when(cacheProperties.getTtl()).thenReturn(ttlConfig);

        // 创建测试用户
        testUser1 = createTestUser(1L, "user1", "user1@example.com");
        testUser2 = createTestUser(2L, "user2", "user2@example.com");
        testUser3 = createTestUser(3L, "user3", "user3@example.com");
    }

    private User createTestUser(Long id, String userName, String email) {
        return User.create(id, userName, email, "hashedPassword");
    }

    @Test
    @DisplayName("测试全部缓存命中场景 - 应该直接从缓存返回，不查询数据库")
    void testBatchQuery_AllCacheHit() {
        // Given: 所有用户都在缓存中
        Set<Long> userIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(UserRedisKeys.userDetail(1L))).thenReturn(testUser1);
        when(valueOperations.get(UserRedisKeys.userDetail(2L))).thenReturn(testUser2);
        when(valueOperations.get(UserRedisKeys.userDetail(3L))).thenReturn(testUser3);

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该返回所有用户
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(testUser1));
        assertTrue(result.contains(testUser2));
        assertTrue(result.contains(testUser3));

        // 验证：不应该查询数据库
        verify(delegate, never()).findByIds(any());
        
        // 验证：不应该获取锁
        verify(redissonClient, never()).getLock(anyString());
        
        // 验证：不应该记录热点数据访问
        verify(hotDataIdentifier, never()).recordAccess(anyString(), anyLong());
    }

    @Test
    @DisplayName("测试部分缓存命中场景 - 应该只查询未命中的数据")
    void testBatchQuery_PartialCacheHit() {
        // Given: user1 和 user2 在缓存中，user3 不在
        Set<Long> userIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(UserRedisKeys.userDetail(1L))).thenReturn(testUser1);
        when(valueOperations.get(UserRedisKeys.userDetail(2L))).thenReturn(testUser2);
        when(valueOperations.get(UserRedisKeys.userDetail(3L))).thenReturn(null);  // 缓存未命中
        
        // user3 是非热点数据
        when(hotDataIdentifier.isHotData("user", 3L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot("user", 3L)).thenReturn(false);
        
        // 数据库返回 user3
        when(delegate.findByIds(Set.of(3L))).thenReturn(List.of(testUser3));

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该返回所有用户
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(testUser1));
        assertTrue(result.contains(testUser2));
        assertTrue(result.contains(testUser3));

        // 验证：只查询未命中的ID
        verify(delegate, times(1)).findByIds(Set.of(3L));
        
        // 验证：应该记录 user3 的访问
        verify(hotDataIdentifier, times(1)).recordAccess("user", 3L);
        
        // 验证：应该缓存 user3
        verify(valueOperations, times(1)).set(
            eq(UserRedisKeys.userDetail(3L)),
            eq(testUser3),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试全部缓存未命中场景 - 应该批量查询数据库")
    void testBatchQuery_AllCacheMiss() {
        // Given: 所有用户都不在缓存中
        Set<Long> userIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 所有用户都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库返回所有用户
        when(delegate.findByIds(userIds)).thenReturn(List.of(testUser1, testUser2, testUser3));

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该返回所有用户
        assertNotNull(result);
        assertEquals(3, result.size());
        assertTrue(result.contains(testUser1));
        assertTrue(result.contains(testUser2));
        assertTrue(result.contains(testUser3));

        // 验证：应该批量查询数据库
        verify(delegate, times(1)).findByIds(userIds);
        
        // 验证：应该记录所有用户的访问
        verify(hotDataIdentifier, times(1)).recordAccess("user", 1L);
        verify(hotDataIdentifier, times(1)).recordAccess("user", 2L);
        verify(hotDataIdentifier, times(1)).recordAccess("user", 3L);
        
        // 验证：应该缓存所有用户
        verify(valueOperations, times(3)).set(
            anyString(),
            any(User.class),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试热点数据使用分布式锁 - 应该按ID排序避免死锁")
    void testBatchQuery_HotDataWithLock() throws InterruptedException {
        // Given: 所有用户都不在缓存中，且都是热点数据
        Set<Long> userIds = Set.of(3L, 1L, 2L);  // 故意乱序
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 所有用户都是热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库返回用户
        when(delegate.findById(1L)).thenReturn(Optional.of(testUser1));
        when(delegate.findById(2L)).thenReturn(Optional.of(testUser2));
        when(delegate.findById(3L)).thenReturn(Optional.of(testUser3));

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该返回所有用户
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证：应该按排序后的顺序获取锁（1, 2, 3）
        // 这是死锁避免的关键：所有线程都按相同顺序获取锁
        verify(redissonClient, times(1)).getLock(UserRedisKeys.lockDetail(1L));
        verify(redissonClient, times(1)).getLock(UserRedisKeys.lockDetail(2L));
        verify(redissonClient, times(1)).getLock(UserRedisKeys.lockDetail(3L));
        
        // 验证：锁应该被释放
        verify(lock, times(3)).unlock();
    }

    @Test
    @DisplayName("测试混合场景 - 热点数据和非热点数据混合")
    void testBatchQuery_MixedHotAndCold() throws InterruptedException {
        // Given: user1 是热点，user2 和 user3 是非热点
        Set<Long> userIds = Set.of(1L, 2L, 3L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // user1 是热点数据
        when(hotDataIdentifier.isHotData("user", 1L)).thenReturn(true);
        when(hotDataIdentifier.isHotData("user", 2L)).thenReturn(false);
        when(hotDataIdentifier.isHotData("user", 3L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 配置锁（仅 user1 使用）
        when(redissonClient.getLock(UserRedisKeys.lockDetail(1L))).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 数据库返回用户
        when(delegate.findById(1L)).thenReturn(Optional.of(testUser1));
        when(delegate.findByIds(Set.of(2L, 3L))).thenReturn(List.of(testUser2, testUser3));

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该返回所有用户
        assertNotNull(result);
        assertEquals(3, result.size());

        // 验证：热点数据使用锁
        verify(redissonClient, times(1)).getLock(UserRedisKeys.lockDetail(1L));
        verify(delegate, times(1)).findById(1L);
        
        // 验证：非热点数据批量查询
        verify(delegate, times(1)).findByIds(Set.of(2L, 3L));
        
        // 验证：锁应该被释放
        verify(lock, times(1)).unlock();
    }

    @Test
    @DisplayName("测试空值缓存 - 不存在的用户应该被缓存为空值")
    void testBatchQuery_NullValueCache() {
        // Given: user1 存在，user2 不存在
        Set<Long> userIds = Set.of(1L, 2L);
        
        when(valueOperations.get(anyString())).thenReturn(null);
        
        // 都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库只返回 user1
        when(delegate.findByIds(userIds)).thenReturn(List.of(testUser1));

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该只返回 user1
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(testUser1));

        // 验证：user1 应该被缓存
        verify(valueOperations, times(1)).set(
            eq(UserRedisKeys.userDetail(1L)),
            eq(testUser1),
            anyLong(),
            eq(TimeUnit.SECONDS)
        );
        
        // 验证：user2 应该被缓存为空值（使用 CacheConstants.NULL_VALUE）
        verify(valueOperations, times(1)).set(
            eq(UserRedisKeys.userDetail(2L)),
            eq(CacheConstants.NULL_VALUE),
            eq(60L),
            eq(TimeUnit.SECONDS)
        );
    }

    @Test
    @DisplayName("测试空集合输入 - 应该返回空列表")
    void testBatchQuery_EmptyInput() {
        // When: 传入空集合
        List<User> result = cachedUserRepository.findByIdsWithCache(new HashSet<>());

        // Then: 应该返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证：不应该有任何操作
        verify(valueOperations, never()).get(anyString());
        verify(delegate, never()).findByIds(any());
    }

    @Test
    @DisplayName("测试null输入 - 应该返回空列表")
    void testBatchQuery_NullInput() {
        // When: 传入null
        List<User> result = cachedUserRepository.findByIdsWithCache(null);

        // Then: 应该返回空列表
        assertNotNull(result);
        assertTrue(result.isEmpty());

        // 验证：不应该有任何操作
        verify(valueOperations, never()).get(anyString());
        verify(delegate, never()).findByIds(any());
    }

    @Test
    @DisplayName("测试缓存异常降级 - Redis异常时应该查询数据库")
    void testBatchQuery_CacheException() {
        // Given: Redis 抛出异常
        Set<Long> userIds = Set.of(1L, 2L);
        
        when(valueOperations.get(anyString())).thenThrow(new RuntimeException("Redis connection failed"));
        
        // 都是非热点数据
        when(hotDataIdentifier.isHotData(anyString(), anyLong())).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot(anyString(), anyLong())).thenReturn(false);
        
        // 数据库返回用户
        when(delegate.findByIds(userIds)).thenReturn(List.of(testUser1, testUser2));

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该降级查询数据库
        assertNotNull(result);
        assertEquals(2, result.size());

        // 验证：应该查询数据库
        verify(delegate, times(1)).findByIds(userIds);
    }

    @Test
    @DisplayName("测试数据库异常处理 - 数据库异常时应该返回已缓存的数据")
    void testBatchQuery_DatabaseException() {
        // Given: user1 在缓存中，user2 不在缓存中
        Set<Long> userIds = Set.of(1L, 2L);
        
        when(valueOperations.get(UserRedisKeys.userDetail(1L))).thenReturn(testUser1);
        when(valueOperations.get(UserRedisKeys.userDetail(2L))).thenReturn(null);
        
        // user2 是非热点数据
        when(hotDataIdentifier.isHotData("user", 2L)).thenReturn(false);
        when(hotDataIdentifier.isManuallyMarkedAsHot("user", 2L)).thenReturn(false);
        
        // 数据库抛出异常
        when(delegate.findByIds(Set.of(2L))).thenThrow(new RuntimeException("Database connection failed"));

        // When: 批量查询
        List<User> result = cachedUserRepository.findByIdsWithCache(userIds);

        // Then: 应该返回缓存中的数据
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.contains(testUser1));
    }
}
