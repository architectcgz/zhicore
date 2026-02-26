package com.zhicore.user.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.domain.model.OutboxEvent;
import com.zhicore.user.domain.model.OutboxEventStatus;
import com.zhicore.user.domain.model.UserBlock;
import com.zhicore.user.domain.repository.OutboxEventRepository;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.BlockDomainService;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 拉黑应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlockApplicationService {

    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final UserFollowRepository userFollowRepository;
    private final BlockDomainService blockDomainService;
    private final OutboxEventRepository outboxEventRepository;
    private final RedissonClient redissonClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final TransactionTemplate transactionTemplate;

    /**
     * 拉黑用户
     *
     * 按 userId 大小排序获取锁防死锁，同一事务内完成拉黑+取消双向关注+写入 Outbox 事件
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     */
    public void block(Long blockerId, Long blockedId) {
        blockDomainService.validateBlock(blockerId, blockedId);

        // 按 userId 大小排序获取锁，防止死锁
        String blockLockKey = UserRedisKeys.blockLock(blockerId, blockedId);
        RLock lock = redissonClient.getLock(blockLockKey);

        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
            }

            if (userBlockRepository.exists(blockerId, blockedId)) {
                return;
            }

            transactionTemplate.executeWithoutResult(status -> {
                userBlockRepository.save(UserBlock.create(blockerId, blockedId));

                // 自动取消双向关注
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

                // 写入 Outbox 事件
                outboxEventRepository.save(new OutboxEvent(
                    UUID.randomUUID().toString(),
                    "user-blocked", "blocked",
                    String.valueOf(blockerId),
                    JSON.toJSONString(Map.of("blockerId", blockerId, "blockedId", blockedId)),
                    OutboxEventStatus.PENDING, LocalDateTime.now()
                ));
            });

            // 删除相关缓存
            evictFollowCaches(blockerId, blockedId);
            log.info("User blocked: blockerId={}, blockedId={}", blockerId, blockedId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 取消拉黑
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     */
    public void unblock(Long blockerId, Long blockedId) {
        blockDomainService.validateUnblock(blockerId, blockedId);

        String blockLockKey = UserRedisKeys.blockLock(blockerId, blockedId);
        RLock lock = redissonClient.getLock(blockLockKey);

        try {
            if (!lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
            }

            if (!userBlockRepository.exists(blockerId, blockedId)) {
                return;
            }

            transactionTemplate.executeWithoutResult(status -> {
                userBlockRepository.delete(blockerId, blockedId);
            });

            log.info("User unblocked: blockerId={}, blockedId={}", blockerId, blockedId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取拉黑列表
     *
     * @param blockerId 拉黑者ID
     * @param page 页码
     * @param size 每页大小
     * @return 被拉黑的用户列表
     */
    public List<UserVO> getBlockedUsers(Long blockerId, int page, int size) {
        // 参数验证
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        
        List<UserBlock> blocks = userBlockRepository.findByBlockerId(blockerId, page, size);
        return blocks.stream()
                .map(block -> userRepository.findById(block.getBlockedId()).orElse(null))
                .filter(user -> user != null)
                .map(UserAssembler::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 检查是否已拉黑
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     * @return 是否已拉黑
     */
    public boolean isBlocked(Long blockerId, Long blockedId) {
        return userBlockRepository.exists(blockerId, blockedId);
    }

    /**
     * 清除双方关注计数缓存
     */
    private void evictFollowCaches(Long userIdA, Long userIdB) {
        try {
            redisTemplate.delete(UserRedisKeys.followingCount(userIdA));
            redisTemplate.delete(UserRedisKeys.followersCount(userIdA));
            redisTemplate.delete(UserRedisKeys.followingCount(userIdB));
            redisTemplate.delete(UserRedisKeys.followersCount(userIdB));
        } catch (Exception e) {
            log.warn("清除关注缓存失败: {}", e.getMessage());
        }
    }
}
