package com.zhicore.user.application.service;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.domain.model.UserBlock;
import com.zhicore.user.domain.repository.UserBlockRepository;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.BlockDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
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
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * 拉黑用户
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     */
    public void block(Long blockerId, Long blockedId) {
        // 1. 业务验证
        blockDomainService.validateBlock(blockerId, blockedId);

        // 2. 分布式锁防止并发
        String lockKey = "block:" + blockerId + ":" + blockedId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
            }

            // 3. 幂等性检查
            if (userBlockRepository.exists(blockerId, blockedId)) {
                log.debug("Already blocked: blockerId={}, blockedId={}", blockerId, blockedId);
                return;
            }

            // 4. 数据库操作在事务中执行
            transactionTemplate.executeWithoutResult(status -> {
                // 保存拉黑关系
                UserBlock block = UserBlock.create(blockerId, blockedId);
                userBlockRepository.save(block);

                // 如果存在关注关系，自动取消
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
        // 1. 业务验证
        blockDomainService.validateUnblock(blockerId, blockedId);

        // 2. 分布式锁防止并发
        String lockKey = "block:" + blockerId + ":" + blockedId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
            }

            // 3. 幂等性检查
            if (!userBlockRepository.exists(blockerId, blockedId)) {
                log.debug("Not blocked: blockerId={}, blockedId={}", blockerId, blockedId);
                return;
            }

            // 4. 数据库操作在事务中执行
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
}
