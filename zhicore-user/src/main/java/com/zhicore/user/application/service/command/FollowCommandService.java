package com.zhicore.user.application.service.command;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.application.port.event.UserIntegrationEventPort;
import com.zhicore.user.application.port.store.FollowStatsStore;
import com.zhicore.user.domain.model.UserFollow;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.service.FollowDomainService;
import com.zhicore.integration.messaging.user.UserFollowedIntegrationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 关注命令服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowCommandService {

    private final UserFollowRepository userFollowRepository;
    private final FollowDomainService followDomainService;
    private final UserIntegrationEventPort eventPublisher;
    private final FollowStatsStore followStatsStore;
    private final LockManager lockManager;
    private final TransactionTemplate transactionTemplate;
    private final UserCacheKeyResolver userCacheKeyResolver;

    public void follow(Long followerId, Long followingId) {
        followDomainService.validateFollow(followerId, followingId);

        String lockKey = userCacheKeyResolver.followLock(followerId, followingId);
        if (!lockManager.tryLock(lockKey, getLockWaitTime(), getLockLeaseTime())) {
            throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
        }

        try {
            if (userFollowRepository.exists(followerId, followingId)) {
                log.debug("Already followed: followerId={}, followingId={}", followerId, followingId);
                return;
            }

            transactionTemplate.executeWithoutResult(status -> {
                UserFollow follow = UserFollow.create(followerId, followingId);
                userFollowRepository.save(follow);
                userFollowRepository.incrementFollowing(followerId);
                userFollowRepository.incrementFollowers(followingId);
                eventPublisher.publish(new UserFollowedIntegrationEvent(
                        newEventId(),
                        Instant.now(),
                        followerId,
                        followingId,
                        null
                ));
            });

            try {
                followStatsStore.incrementFollowingCount(followerId);
                followStatsStore.incrementFollowersCount(followingId);
            } catch (Exception e) {
                log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
            }
            log.info("User followed: followerId={}, followingId={}", followerId, followingId);
        } finally {
            lockManager.unlock(lockKey);
        }
    }

    public void unfollow(Long followerId, Long followingId) {
        followDomainService.validateUnfollow(followerId, followingId);

        String lockKey = userCacheKeyResolver.followLock(followerId, followingId);
        if (!lockManager.tryLock(lockKey, getLockWaitTime(), getLockLeaseTime())) {
            throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
        }

        try {
            if (!userFollowRepository.exists(followerId, followingId)) {
                log.debug("Not followed: followerId={}, followingId={}", followerId, followingId);
                return;
            }

            transactionTemplate.executeWithoutResult(status -> {
                userFollowRepository.delete(followerId, followingId);
                userFollowRepository.decrementFollowing(followerId);
                userFollowRepository.decrementFollowers(followingId);
            });

            try {
                followStatsStore.decrementFollowingCount(followerId);
                followStatsStore.decrementFollowersCount(followingId);
            } catch (Exception e) {
                log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
            }

            log.info("User unfollowed: followerId={}, followingId={}", followerId, followingId);
        } finally {
            lockManager.unlock(lockKey);
        }
    }

    private Duration getLockWaitTime() {
        return Duration.ofSeconds(5);
    }

    private Duration getLockLeaseTime() {
        return Duration.ofSeconds(10);
    }

    private String newEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
