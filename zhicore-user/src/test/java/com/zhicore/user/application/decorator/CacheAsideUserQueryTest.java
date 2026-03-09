package com.zhicore.user.application.decorator;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.cache.port.CacheRepository;
import com.zhicore.common.cache.port.CacheResult;
import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.config.CacheProperties;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CacheAsideUserQuery 单元测试
 *
 * @author ZhiCore Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CacheAsideUserQuery 缓存装饰器测试")
class CacheAsideUserQueryTest {

    @Mock
    private UserQueryPort delegate;

    @Mock
    private CacheRepository cacheRepository;

    @Mock
    private LockManager lockManager;

    private CacheProperties cacheProperties;

    private CacheAsideUserQuery cacheAsideUserQuery;

    @BeforeEach
    void setUp() {
        cacheProperties = new CacheProperties();
        cacheProperties.setTtl(new CacheProperties.Ttl());
        cacheProperties.setJitter(new CacheProperties.Jitter());

        cacheAsideUserQuery = new CacheAsideUserQuery(
                delegate, cacheRepository, lockManager, cacheProperties
        );
    }

    private UserVO buildUserVO(Long userId) {
        UserVO vo = new UserVO();
        vo.setId(userId);
        vo.setUserName("testuser");
        vo.setNickName("测试用户");
        return vo;
    }

    private UserSimpleDTO buildSimpleDTO(Long userId) {
        UserSimpleDTO dto = new UserSimpleDTO();
        dto.setId(userId);
        dto.setUserName("testuser");
        dto.setNickname("测试用户");
        return dto;
    }

    @Nested
    @DisplayName("getUserById - 用户详情查询")
    class GetUserById {

        @Test
        @DisplayName("缓存命中时应直接返回，不查数据源")
        void shouldReturnFromCacheWhenHit() {
            UserVO cached = buildUserVO(1L);
            when(cacheRepository.get(UserRedisKeys.userDetail(1L), UserVO.class))
                    .thenReturn(CacheResult.hit(cached));

            UserVO result = cacheAsideUserQuery.getUserById(1L);

            assertSame(cached, result);
            verifyNoInteractions(delegate);
            verifyNoInteractions(lockManager);
        }

        @Test
        @DisplayName("缓存为空值标记时应返回 null，不查数据源")
        void shouldReturnNullWhenCacheIsNullValue() {
            when(cacheRepository.get(UserRedisKeys.userDetail(1L), UserVO.class))
                    .thenReturn(CacheResult.nullValue());

            UserVO result = cacheAsideUserQuery.getUserById(1L);

            assertNull(result);
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("缓存未命中且获取锁成功时，应查数据源并回填缓存")
        void shouldFetchFromSourceAndCacheWhenMissAndLockAcquired() {
            UserVO fromDb = buildUserVO(1L);
            String cacheKey = UserRedisKeys.userDetail(1L);
            String lockKey = UserRedisKeys.lockDetail(1L);

            when(cacheRepository.get(cacheKey, UserVO.class))
                    .thenReturn(CacheResult.miss())  // 第一次 miss
                    .thenReturn(CacheResult.miss());  // DCL 也 miss
            when(lockManager.tryLock(eq(lockKey), any(Duration.class), any(Duration.class)))
                    .thenReturn(true);
            when(delegate.getUserById(1L)).thenReturn(fromDb);

            UserVO result = cacheAsideUserQuery.getUserById(1L);

            assertSame(fromDb, result);
            verify(cacheRepository).set(eq(cacheKey), eq(fromDb), any(Duration.class));
            verify(lockManager).unlock(lockKey);
        }

        @Test
        @DisplayName("缓存未命中但 DCL 命中时，应返回缓存值不查数据源")
        void shouldReturnFromCacheAfterDCLHit() {
            UserVO cached = buildUserVO(1L);
            String cacheKey = UserRedisKeys.userDetail(1L);
            String lockKey = UserRedisKeys.lockDetail(1L);

            when(cacheRepository.get(cacheKey, UserVO.class))
                    .thenReturn(CacheResult.miss())    // 第一次 miss
                    .thenReturn(CacheResult.hit(cached)); // DCL hit
            when(lockManager.tryLock(eq(lockKey), any(Duration.class), any(Duration.class)))
                    .thenReturn(true);

            UserVO result = cacheAsideUserQuery.getUserById(1L);

            assertSame(cached, result);
            verifyNoInteractions(delegate);
            verify(lockManager).unlock(lockKey);
        }

        @Test
        @DisplayName("获取锁失败时应降级直接查数据源")
        void shouldFallbackToSourceWhenLockFailed() {
            UserVO fromDb = buildUserVO(1L);
            String cacheKey = UserRedisKeys.userDetail(1L);
            String lockKey = UserRedisKeys.lockDetail(1L);

            when(cacheRepository.get(cacheKey, UserVO.class))
                    .thenReturn(CacheResult.miss());
            when(lockManager.tryLock(eq(lockKey), any(Duration.class), any(Duration.class)))
                    .thenReturn(false);
            when(delegate.getUserById(1L)).thenReturn(fromDb);

            UserVO result = cacheAsideUserQuery.getUserById(1L);

            assertSame(fromDb, result);
            verify(lockManager, never()).unlock(anyString());
        }

        @Test
        @DisplayName("数据源返回 null 时应缓存空值标记")
        void shouldCacheNullValueWhenSourceReturnsNull() {
            String cacheKey = UserRedisKeys.userDetail(1L);
            String lockKey = UserRedisKeys.lockDetail(1L);

            when(cacheRepository.get(cacheKey, UserVO.class))
                    .thenReturn(CacheResult.miss())
                    .thenReturn(CacheResult.miss());
            when(lockManager.tryLock(eq(lockKey), any(Duration.class), any(Duration.class)))
                    .thenReturn(true);
            when(delegate.getUserById(1L)).thenReturn(null);

            UserVO result = cacheAsideUserQuery.getUserById(1L);

            assertNull(result);
            verify(cacheRepository).setIfAbsent(eq(cacheKey), isNull(), any(Duration.class));
            verify(lockManager).unlock(lockKey);
        }
    }

    @Nested
    @DisplayName("getUserSimpleById - 用户简要信息查询")
    class GetUserSimpleById {

        @Test
        @DisplayName("缓存命中时应直接返回")
        void shouldReturnFromCacheWhenHit() {
            UserSimpleDTO cached = buildSimpleDTO(1L);
            when(cacheRepository.get(UserRedisKeys.userSimple(1L), UserSimpleDTO.class))
                    .thenReturn(CacheResult.hit(cached));

            UserSimpleDTO result = cacheAsideUserQuery.getUserSimpleById(1L);

            assertSame(cached, result);
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("缓存为空值标记时应返回 null")
        void shouldReturnNullWhenCacheIsNullValue() {
            when(cacheRepository.get(UserRedisKeys.userSimple(1L), UserSimpleDTO.class))
                    .thenReturn(CacheResult.nullValue());

            UserSimpleDTO result = cacheAsideUserQuery.getUserSimpleById(1L);

            assertNull(result);
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("缓存未命中时应查数据源并用 setIfAbsent 回填")
        void shouldFetchAndSetIfAbsentWhenMiss() {
            UserSimpleDTO fromDb = buildSimpleDTO(1L);
            String cacheKey = UserRedisKeys.userSimple(1L);

            when(cacheRepository.get(cacheKey, UserSimpleDTO.class))
                    .thenReturn(CacheResult.miss());
            when(delegate.getUserSimpleById(1L)).thenReturn(fromDb);

            UserSimpleDTO result = cacheAsideUserQuery.getUserSimpleById(1L);

            assertSame(fromDb, result);
            verify(cacheRepository).setIfAbsent(eq(cacheKey), eq(fromDb), any(Duration.class));
        }

        @Test
        @DisplayName("数据源返回 null 时应用 setIfAbsent 缓存空值")
        void shouldCacheNullWithSetIfAbsent() {
            String cacheKey = UserRedisKeys.userSimple(1L);

            when(cacheRepository.get(cacheKey, UserSimpleDTO.class))
                    .thenReturn(CacheResult.miss());
            when(delegate.getUserSimpleById(1L)).thenReturn(null);

            UserSimpleDTO result = cacheAsideUserQuery.getUserSimpleById(1L);

            assertNull(result);
            verify(cacheRepository).setIfAbsent(eq(cacheKey), isNull(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("batchGetUsersSimple - 批量查询")
    class BatchGetUsersSimple {

        @Test
        @DisplayName("空集合应直接返回空 Map")
        void shouldReturnEmptyMapForEmptyInput() {
            Map<Long, UserSimpleDTO> result = cacheAsideUserQuery.batchGetUsersSimple(Set.of());
            assertTrue(result.isEmpty());
            verifyNoInteractions(cacheRepository);
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("null 输入应直接返回空 Map")
        void shouldReturnEmptyMapForNullInput() {
            Map<Long, UserSimpleDTO> result = cacheAsideUserQuery.batchGetUsersSimple(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("全部缓存命中时不应查数据源")
        void shouldNotQuerySourceWhenAllCacheHit() {
            UserSimpleDTO dto1 = buildSimpleDTO(1L);
            UserSimpleDTO dto2 = buildSimpleDTO(2L);

            when(cacheRepository.get(UserRedisKeys.userSimple(1L), UserSimpleDTO.class))
                    .thenReturn(CacheResult.hit(dto1));
            when(cacheRepository.get(UserRedisKeys.userSimple(2L), UserSimpleDTO.class))
                    .thenReturn(CacheResult.hit(dto2));

            Map<Long, UserSimpleDTO> result = cacheAsideUserQuery.batchGetUsersSimple(Set.of(1L, 2L));

            assertEquals(2, result.size());
            assertSame(dto1, result.get(1L));
            assertSame(dto2, result.get(2L));
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("部分缓存未命中时应只查未命中的 ID 并回填")
        void shouldOnlyQueryMissedIdsFromSource() {
            UserSimpleDTO cached1 = buildSimpleDTO(1L);
            UserSimpleDTO fromDb2 = buildSimpleDTO(2L);

            when(cacheRepository.get(UserRedisKeys.userSimple(1L), UserSimpleDTO.class))
                    .thenReturn(CacheResult.hit(cached1));
            when(cacheRepository.get(UserRedisKeys.userSimple(2L), UserSimpleDTO.class))
                    .thenReturn(CacheResult.miss());

            Map<Long, UserSimpleDTO> dbResult = new HashMap<>();
            dbResult.put(2L, fromDb2);
            when(delegate.batchGetUsersSimple(Set.of(2L))).thenReturn(dbResult);

            Map<Long, UserSimpleDTO> result = cacheAsideUserQuery.batchGetUsersSimple(Set.of(1L, 2L));

            assertEquals(2, result.size());
            assertSame(cached1, result.get(1L));
            assertSame(fromDb2, result.get(2L));
            verify(cacheRepository).setIfAbsent(eq(UserRedisKeys.userSimple(2L)), eq(fromDb2), any(Duration.class));
        }

        @Test
        @DisplayName("空值标记的 ID 应跳过，不查数据源")
        void shouldSkipNullValueIds() {
            when(cacheRepository.get(UserRedisKeys.userSimple(1L), UserSimpleDTO.class))
                    .thenReturn(CacheResult.nullValue());

            Map<Long, UserSimpleDTO> result = cacheAsideUserQuery.batchGetUsersSimple(Set.of(1L));

            assertTrue(result.isEmpty());
            verifyNoInteractions(delegate);
        }
    }

    @Nested
    @DisplayName("isStrangerMessageAllowed - 陌生人消息设置查询")
    class IsStrangerMessageAllowed {

        @Test
        @DisplayName("缓存命中时应直接返回布尔值")
        void shouldReturnBooleanFromCacheWhenHit() {
            String cacheKey = UserRedisKeys.strangerMessageSetting(1L);
            when(cacheRepository.get(cacheKey, Boolean.class))
                    .thenReturn(CacheResult.hit(Boolean.FALSE));

            boolean result = cacheAsideUserQuery.isStrangerMessageAllowed(1L);

            assertFalse(result);
            verifyNoInteractions(delegate);
        }

        @Test
        @DisplayName("缓存未命中时应查数据源并回填缓存")
        void shouldFetchSettingFromSourceAndCacheWhenMiss() {
            String cacheKey = UserRedisKeys.strangerMessageSetting(1L);
            when(cacheRepository.get(cacheKey, Boolean.class))
                    .thenReturn(CacheResult.miss());
            when(delegate.isStrangerMessageAllowed(1L)).thenReturn(false);

            boolean result = cacheAsideUserQuery.isStrangerMessageAllowed(1L);

            assertFalse(result);
            verify(cacheRepository).set(eq(cacheKey), eq(false), any(Duration.class));
        }
    }
}
