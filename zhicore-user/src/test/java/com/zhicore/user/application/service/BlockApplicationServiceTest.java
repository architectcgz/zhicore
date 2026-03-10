package com.zhicore.user.application.service;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.application.port.store.FollowStatsStore;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.BlockDomainService;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BlockApplicationService 测试")
class BlockApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserBlockRepository userBlockRepository;

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private BlockDomainService blockDomainService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private FollowStatsStore followStatsStore;

    @Mock
    private LockManager lockManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    private UserCacheKeyResolver userCacheKeyResolver;

    private BlockApplicationService blockApplicationService;

    @BeforeEach
    void setUp() {
        userCacheKeyResolver = new com.zhicore.user.infrastructure.cache.DefaultUserCacheKeyResolver();
        blockApplicationService = new BlockApplicationService(
                userRepository,
                userBlockRepository,
                userFollowRepository,
                blockDomainService,
                outboxEventRepository,
                followStatsStore,
                lockManager,
                transactionTemplate,
                userCacheKeyResolver
        );
    }

    @Test
    @DisplayName("block 应通过多锁保护并清理关注统计缓存")
    void blockShouldUseMultiLockAndEvictFollowStats() {
        Long blockerId = 101L;
        Long blockedId = 202L;
        List<String> expectedLockKeys = List.of(
                UserRedisKeys.blockLock(blockerId, blockedId),
                UserRedisKeys.followLock(blockerId, blockedId),
                UserRedisKeys.followLock(blockedId, blockerId)
        );

        when(lockManager.tryLockAll(expectedLockKeys, Duration.ofSeconds(3), Duration.ofSeconds(10))).thenReturn(true);
        when(userBlockRepository.exists(blockerId, blockedId)).thenReturn(false);
        when(userFollowRepository.exists(blockerId, blockedId)).thenReturn(true);
        when(userFollowRepository.exists(blockedId, blockerId)).thenReturn(true);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        blockApplicationService.block(blockerId, blockedId);

        verify(lockManager).tryLockAll(expectedLockKeys, Duration.ofSeconds(3), Duration.ofSeconds(10));
        verify(userBlockRepository).save(any());
        verify(userFollowRepository).delete(blockerId, blockedId);
        verify(userFollowRepository).delete(blockedId, blockerId);
        verify(followStatsStore).evictStats(blockerId, blockedId);
        verify(lockManager).unlockAll(expectedLockKeys);
    }

    @Test
    @DisplayName("block 在未获取到多锁时应直接失败")
    void blockShouldFailWhenMultiLockNotAcquired() {
        Long blockerId = 101L;
        Long blockedId = 202L;
        List<String> expectedLockKeys = List.of(
                UserRedisKeys.blockLock(blockerId, blockedId),
                UserRedisKeys.followLock(blockerId, blockedId),
                UserRedisKeys.followLock(blockedId, blockerId)
        );
        when(lockManager.tryLockAll(expectedLockKeys, Duration.ofSeconds(3), Duration.ofSeconds(10))).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> blockApplicationService.block(blockerId, blockedId));

        assertEquals(ResultCode.REQUEST_TOO_FREQUENT.getCode(), exception.getCode());
        verify(userBlockRepository, never()).exists(any(), any());
        verify(lockManager, never()).unlockAll(any());
    }

    @Test
    @DisplayName("unblock 应使用单锁并在结束后释放")
    void unblockShouldUseSingleLockAndUnlock() {
        Long blockerId = 101L;
        Long blockedId = 202L;
        String blockLockKey = UserRedisKeys.blockLock(blockerId, blockedId);

        when(lockManager.tryLock(blockLockKey, Duration.ofSeconds(3), Duration.ofSeconds(10))).thenReturn(true);
        when(userBlockRepository.exists(blockerId, blockedId)).thenReturn(true);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        blockApplicationService.unblock(blockerId, blockedId);

        verify(lockManager).tryLock(blockLockKey, Duration.ofSeconds(3), Duration.ofSeconds(10));
        verify(userBlockRepository).delete(blockerId, blockedId);
        verify(lockManager).unlock(blockLockKey);
    }
}
