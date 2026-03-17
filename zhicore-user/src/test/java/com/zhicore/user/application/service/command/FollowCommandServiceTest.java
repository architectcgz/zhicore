package com.zhicore.user.application.service.command;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.integration.messaging.user.UserFollowedIntegrationEvent;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.application.port.event.UserIntegrationEventPort;
import com.zhicore.user.application.port.store.FollowStatsStore;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.service.FollowDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FollowCommandServiceTest {

    @Mock
    private UserFollowRepository userFollowRepository;

    @Mock
    private FollowDomainService followDomainService;

    @Mock
    private UserIntegrationEventPort eventPublisher;

    @Mock
    private FollowStatsStore followStatsStore;

    @Mock
    private LockManager lockManager;

    @Mock
    private TransactionTemplate transactionTemplate;

    private UserCacheKeyResolver userCacheKeyResolver;

    private FollowCommandService followCommandService;

    @BeforeEach
    void setUp() {
        userCacheKeyResolver = new com.zhicore.user.infrastructure.cache.DefaultUserCacheKeyResolver();
        followCommandService = new FollowCommandService(
                userFollowRepository,
                followDomainService,
                eventPublisher,
                followStatsStore,
                lockManager,
                transactionTemplate,
                userCacheKeyResolver
        );
    }

    @Test
    @DisplayName("follow 应通过 LockManager 加锁并在结束后释放")
    void followShouldUseLockManagerAndUnlock() {
        Long followerId = 101L;
        Long followingId = 202L;
        String lockKey = userCacheKeyResolver.followLock(followerId, followingId);

        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10))).thenReturn(true);
        when(userFollowRepository.exists(followerId, followingId)).thenReturn(false);
        doAnswer(invocation -> {
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        followCommandService.follow(followerId, followingId);

        verify(lockManager).tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10));
        verify(userFollowRepository).save(any());
        verify(userFollowRepository).incrementFollowing(followerId);
        verify(userFollowRepository).incrementFollowers(followingId);
        verify(followStatsStore).incrementFollowingCount(followerId);
        verify(followStatsStore).incrementFollowersCount(followingId);
        verify(eventPublisher).publish(any(UserFollowedIntegrationEvent.class));
        verify(lockManager).unlock(lockKey);
    }

    @Test
    @DisplayName("follow 在锁未获取到时应直接失败")
    void followShouldFailWhenLockNotAcquired() {
        Long followerId = 101L;
        Long followingId = 202L;
        String lockKey = userCacheKeyResolver.followLock(followerId, followingId);

        when(lockManager.tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10))).thenReturn(false);

        assertThrows(BusinessException.class, () -> followCommandService.follow(followerId, followingId));

        verify(lockManager).tryLock(lockKey, Duration.ofSeconds(5), Duration.ofSeconds(10));
        verify(userFollowRepository, never()).exists(any(), any());
        verify(lockManager, never()).unlock(eq(lockKey));
    }
}
