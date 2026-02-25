package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.cache.CacheConstants;
import com.zhicore.common.cache.HotDataIdentifier;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import com.zhicore.user.domain.model.UserStatus;
import net.jqwik.api.*;
import net.jqwik.api.constraints.LongRange;
import net.jqwik.api.constraints.StringLength;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Feature: cache-penetration-protection, Property 3: 缓存填充后的一致性
 * Validates: Requirements 1.3
 * 
 * 属性测试：验证用户服务缓存填充后的一致性
 * 
 * 测试属性：
 * For any 用户ID，当第一个请求成功加载数据并写入缓存后，
 * 所有后续请求都应该能够从缓存中读取到相同的数据。
 * 
 * @author ZhiCore Team
 */
@DisplayName("Property 3: 用户服务缓存一致性属性测试")
class CachedUserRepositoryCacheConsistencyPropertyTest {

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

        // 配置 RedisTemplate mock
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 配置 CacheProperties mock
        when(cacheProperties.getLock()).thenReturn(lockProperties);
        when(cacheProperties.getTtl()).thenReturn(ttlProperties);
        when(lockProperties.getWaitTime()).thenReturn(5L);
        when(lockProperties.getLeaseTime()).thenReturn(10L);
        when(ttlProperties.getEntityDetail()).thenReturn(600L);

        // 创建被测试对象
        cachedRepository = new CachedUserRepository(
                redisTemplate,
                cacheProperties,
                redissonClient,
                hotDataIdentifier,
                delegate
        );
    }

    /**
     * Property 3: 缓存填充后的一致性
     * 
     * For any 随机生成的用户ID和用户数据，当第一个请求成功加载数据并写入缓存后，
     * 后续所有请求都应该从缓存中读取到相同的数据，且与数据库数据一致。
     */
    @Property(tries = 100)
    @DisplayName("Property 3: 缓存填充后数据与数据库一致")
    void testCacheConsistency_CachedDataMatchesDatabase(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId,
            @ForAll @StringLength(min = 3, max = 20) String username,
            @ForAll @StringLength(min = 5, max = 50) String email) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // 创建数据库中的原始数据
        User dbUser = createTestUser(userId, username, email);
        
        // 用于存储写入缓存的数据
        AtomicReference<Object> cachedData = new AtomicReference<>();
        
        // 配置缓存行为
        when(valueOperations.get(cacheKey))
                .thenReturn(null)  // 第一次查询：缓存未命中
                .thenAnswer(invocation -> cachedData.get());  // 第二次查询：返回缓存的数据
        
        // 配置缓存写入：捕获写入的数据
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            cachedData.set(value);
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        // 配置热点数据识别
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        when(hotDataIdentifier.isManuallyMarkedAsHot(ENTITY_TYPE_USER, userId)).thenReturn(false);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询：返回原始数据
        when(delegate.findById(userId)).thenReturn(Optional.of(dbUser));
        
        // When: 第一次查询（缓存未命中，从数据库加载）
        Optional<User> firstResult = cachedRepository.findById(userId);
        
        // Then: 验证第一次查询结果
        assertTrue(firstResult.isPresent(), "第一次查询应该返回数据");
        User firstUser = firstResult.get();
        assertEquals(dbUser.getId(), firstUser.getId(), "用户ID应该一致");
        assertEquals(dbUser.getUserName(), firstUser.getUserName(), "用户名应该一致");
        assertEquals(dbUser.getEmail(), firstUser.getEmail(), "邮箱应该一致");
        
        // 验证数据被写入缓存
        assertNotNull(cachedData.get(), "数据应该被写入缓存");
        
        // 验证缓存中的数据与数据库数据一致
        User cachedUser = (User) cachedData.get();
        assertEquals(dbUser.getId(), cachedUser.getId(), 
                "缓存中的用户ID应该与数据库一致");
        assertEquals(dbUser.getUserName(), cachedUser.getUserName(), 
                "缓存中的用户名应该与数据库一致");
        assertEquals(dbUser.getEmail(), cachedUser.getEmail(), 
                "缓存中的邮箱应该与数据库一致");
        
        // When: 第二次查询（从缓存读取）
        Optional<User> secondResult = cachedRepository.findById(userId);
        
        // Then: 验证第二次查询结果
        assertTrue(secondResult.isPresent(), "第二次查询应该返回数据");
        User secondUser = secondResult.get();
        assertEquals(dbUser.getId(), secondUser.getId(), 
                "第二次查询的用户ID应该与数据库一致");
        assertEquals(dbUser.getUserName(), secondUser.getUserName(), 
                "第二次查询的用户名应该与数据库一致");
        assertEquals(dbUser.getEmail(), secondUser.getEmail(), 
                "第二次查询的邮箱应该与数据库一致");
        
        // 验证两次查询结果一致
        assertEquals(firstUser.getId(), secondUser.getId(), 
                "两次查询的用户ID应该一致");
        assertEquals(firstUser.getUserName(), secondUser.getUserName(), 
                "两次查询的用户名应该一致");
        assertEquals(firstUser.getEmail(), secondUser.getEmail(), 
                "两次查询的邮箱应该一致");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).findById(userId);
        
        // 验证缓存被写入一次
        verify(valueOperations, times(1)).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 3 变体：验证空值缓存的一致性
     * 
     * 当数据库中不存在用户时，应该缓存空值，后续查询应该返回一致的空值。
     */
    @Property(tries = 100)
    @DisplayName("Property 3 变体: 空值缓存一致性")
    void testCacheConsistency_NullValueConsistency(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // 用于存储写入缓存的数据
        AtomicReference<Object> cachedData = new AtomicReference<>();
        
        // 配置缓存行为
        when(valueOperations.get(cacheKey))
                .thenReturn(null)  // 第一次查询：缓存未命中
                .thenAnswer(invocation -> cachedData.get());  // 第二次查询：返回缓存的空值
        
        // 配置缓存写入：捕获写入的数据
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            cachedData.set(value);
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        // 配置热点数据识别
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询：返回空（用户不存在）
        when(delegate.findById(userId)).thenReturn(Optional.empty());
        
        // When: 第一次查询（缓存未命中，从数据库加载）
        Optional<User> firstResult = cachedRepository.findById(userId);
        
        // Then: 验证第一次查询结果
        assertFalse(firstResult.isPresent(), "第一次查询应该返回空");
        
        // 验证空值被写入缓存
        assertNotNull(cachedData.get(), "空值应该被写入缓存");
        assertEquals(CacheConstants.NULL_VALUE, cachedData.get(), 
                "缓存中应该是NULL_VALUE标记");
        
        // When: 第二次查询（从缓存读取空值）
        Optional<User> secondResult = cachedRepository.findById(userId);
        
        // Then: 验证第二次查询结果
        assertFalse(secondResult.isPresent(), "第二次查询应该返回空");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).findById(userId);
        
        // 验证缓存被写入一次
        verify(valueOperations, times(1)).set(eq(cacheKey), eq(CacheConstants.NULL_VALUE), 
                anyLong(), any(TimeUnit.class));
    }

    /**
     * Property 3 变体：验证多字段数据的一致性
     * 
     * 验证用户对象的所有字段在缓存和数据库之间保持一致。
     */
    @Property(tries = 100)
    @DisplayName("Property 3 变体: 多字段数据一致性")
    void testCacheConsistency_AllFieldsConsistent(
            @ForAll @LongRange(min = 1L, max = 100000L) Long userId,
            @ForAll @StringLength(min = 3, max = 20) String username,
            @ForAll @StringLength(min = 5, max = 50) String email,
            @ForAll @StringLength(min = 0, max = 100) String bio) throws Exception {
        
        // Given: 准备测试数据
        String cacheKey = UserRedisKeys.userDetail(userId);
        String lockKey = UserRedisKeys.lockDetail(userId);
        
        // 创建包含多个字段的用户数据
        User dbUser = createTestUser(userId, username, email);
        // Note: User is immutable, bio is set during reconstitute
        // We need to recreate with bio
        dbUser = User.reconstitute(
            userId,
            username,
            username,
            email,
            "hashedpassword",
            null,
            bio,
            UserStatus.ACTIVE,
            false,
            new java.util.HashSet<>(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
        
        // 用于存储写入缓存的数据
        AtomicReference<Object> cachedData = new AtomicReference<>();
        
        // 配置缓存行为
        when(valueOperations.get(cacheKey))
                .thenReturn(null)
                .thenAnswer(invocation -> cachedData.get());
        
        // 配置缓存写入
        doAnswer(invocation -> {
            Object value = invocation.getArgument(1);
            cachedData.set(value);
            return null;
        }).when(valueOperations).set(eq(cacheKey), any(), anyLong(), any(TimeUnit.class));
        
        // 配置热点数据识别
        when(hotDataIdentifier.isHotData(ENTITY_TYPE_USER, userId)).thenReturn(true);
        
        // 配置锁
        when(redissonClient.getLock(lockKey)).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        
        // 配置数据库查询
        when(delegate.findById(userId)).thenReturn(Optional.of(dbUser));
        
        // When: 第一次查询
        Optional<User> firstResult = cachedRepository.findById(userId);
        
        // Then: 验证所有字段与数据库一致
        assertTrue(firstResult.isPresent(), "第一次查询应该返回数据");
        User firstUser = firstResult.get();
        assertUserEquals(dbUser, firstUser, "第一次查询结果应该与数据库一致");
        
        // 验证缓存中的所有字段与数据库一致
        assertNotNull(cachedData.get(), "数据应该被写入缓存");
        User cachedUser = (User) cachedData.get();
        assertUserEquals(dbUser, cachedUser, "缓存数据应该与数据库一致");
        
        // When: 第二次查询
        Optional<User> secondResult = cachedRepository.findById(userId);
        
        // Then: 验证所有字段与第一次查询一致
        assertTrue(secondResult.isPresent(), "第二次查询应该返回数据");
        User secondUser = secondResult.get();
        assertUserEquals(firstUser, secondUser, "两次查询结果应该一致");
        
        // 验证数据库只被查询一次
        verify(delegate, times(1)).findById(userId);
    }

    // ==================== 辅助方法 ====================

    private User createTestUser(Long userId, String username, String email) {
        return User.reconstitute(
            userId,
            username,
            username,
            email,
            "hashedpassword",
            null,
            null,
            UserStatus.ACTIVE,
            false,
            new java.util.HashSet<>(),
            LocalDateTime.now(),
            LocalDateTime.now()
        );
    }

    private void assertUserEquals(User expected, User actual, String message) {
        assertEquals(expected.getId(), actual.getId(), message + " - ID");
        assertEquals(expected.getUserName(), actual.getUserName(), message + " - 用户名");
        assertEquals(expected.getEmail(), actual.getEmail(), message + " - 邮箱");
        assertEquals(expected.getStatus(), actual.getStatus(), message + " - 状态");
        // Bio comparison - both can be null
        if (expected.getBio() != null || actual.getBio() != null) {
            assertEquals(expected.getBio(), actual.getBio(), message + " - 简介");
        }
    }
}
