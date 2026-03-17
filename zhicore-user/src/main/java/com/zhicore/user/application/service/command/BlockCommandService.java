package com.zhicore.user.application.service.command;

import com.zhicore.common.cache.port.LockManager;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.port.UserCacheKeyResolver;
import com.zhicore.user.application.port.store.FollowStatsStore;
import com.zhicore.user.domain.model.UserBlock;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.service.BlockDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

/**
 * 拉黑命令服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockCommandService {

    private static final Duration BLOCK_LOCK_WAIT_TIME = Duration.ofSeconds(3);
    private static final Duration BLOCK_LOCK_LEASE_TIME = Duration.ofSeconds(10);

    private final UserBlockRepository userBlockRepository;
    private final UserFollowRepository userFollowRepository;
    private final BlockDomainService blockDomainService;
    private final FollowStatsStore followStatsStore;
    private final LockManager lockManager;
    private final TransactionTemplate transactionTemplate;
    private final UserCacheKeyResolver userCacheKeyResolver;

    public void block(Long blockerId, Long blockedId) {
        blockDomainService.validateBlock(blockerId, blockedId);

        List<String> lockKeys = buildBlockLockKeys(blockerId, blockedId);
        if (!lockManager.tryLockAll(lockKeys, BLOCK_LOCK_WAIT_TIME, BLOCK_LOCK_LEASE_TIME)) {
            throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
        }

        try {
            doBlock(blockerId, blockedId);
        } finally {
            lockManager.unlockAll(lockKeys);
        }
    }

    public void unblock(Long blockerId, Long blockedId) {
        blockDomainService.validateUnblock(blockerId, blockedId);

        String blockLockKey = userCacheKeyResolver.blockLock(blockerId, blockedId);
        if (!lockManager.tryLock(blockLockKey, BLOCK_LOCK_WAIT_TIME, BLOCK_LOCK_LEASE_TIME)) {
            throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
        }

        try {
            if (!userBlockRepository.exists(blockerId, blockedId)) {
                return;
            }

            transactionTemplate.executeWithoutResult(status -> userBlockRepository.delete(blockerId, blockedId));
            log.info("User unblocked: blockerId={}, blockedId={}", blockerId, blockedId);
        } finally {
            lockManager.unlock(blockLockKey);
        }
    }

    private void doBlock(Long blockerId, Long blockedId) {
        if (userBlockRepository.exists(blockerId, blockedId)) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            userBlockRepository.save(UserBlock.create(blockerId, blockedId));

            if (userFollowRepository.exists(blockerId, blockedId)) {
                userFollowRepository.delete(blockerId, blockedId);
                userFollowRepository.decrementFollowing(blockerId);
                userFollowRepository.decrementFollowers(blockedId);
            }
            if (userFollowRepository.exists(blockedId, blockerId)) {
                userFollowRepository.delete(blockedId, blockerId);
                userFollowRepository.decrementFollowing(blockedId);
                userFollowRepository.decrementFollowers(blockerId);
            }
        });

        evictFollowCaches(blockerId, blockedId);
        log.info("User blocked: blockerId={}, blockedId={}", blockerId, blockedId);
    }

    private void evictFollowCaches(Long userIdA, Long userIdB) {
        try {
            followStatsStore.evictStats(userIdA, userIdB);
        } catch (Exception e) {
            log.warn("清除关注缓存失败: {}", e.getMessage());
        }
    }

    private List<String> buildBlockLockKeys(Long blockerId, Long blockedId) {
        long minId = Math.min(blockerId, blockedId);
        long maxId = Math.max(blockerId, blockedId);
        return List.of(
                userCacheKeyResolver.blockLock(blockerId, blockedId),
                userCacheKeyResolver.followLock(minId, maxId),
                userCacheKeyResolver.followLock(maxId, minId)
        );
    }
}
