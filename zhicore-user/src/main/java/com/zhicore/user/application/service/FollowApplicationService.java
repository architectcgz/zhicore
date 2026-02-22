package com.zhicore.user.application.service;

import com.zhicore.api.event.user.UserFollowedEvent;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.dto.FollowStatsVO;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.domain.model.UserFollow;
import com.zhicore.user.domain.model.UserFollowStats;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.domain.service.FollowDomainService;
import com.zhicore.user.infrastructure.cache.UserRedisKeys;
import com.zhicore.user.infrastructure.mq.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 关注应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowApplicationService {

    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final FollowDomainService followDomainService;
    private final EventPublisher eventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * 关注用户
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     */
    public void follow(Long followerId, Long followingId) {
        // 1. 业务验证
        followDomainService.validateFollow(followerId, followingId);

        // 2. 分布式锁防止并发
        String lockKey = "follow:" + followerId + ":" + followingId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
            }

            // 3. 幂等性检查
            if (userFollowRepository.exists(followerId, followingId)) {
                log.debug("Already followed: followerId={}, followingId={}", followerId, followingId);
                return;
            }

            // 4. 数据库操作在事务中执行
            transactionTemplate.executeWithoutResult(status -> {
                // 生成ID - 不再需要，使用复合主键
                // 保存关注关系
                UserFollow follow = UserFollow.create(followerId, followingId);
                userFollowRepository.save(follow);

                // 更新数据库统计表
                userFollowRepository.incrementFollowing(followerId);
                userFollowRepository.incrementFollowers(followingId);
            });

            // 5. 事务提交成功后，更新 Redis 缓存
            try {
                redisTemplate.opsForValue().increment(UserRedisKeys.followingCount(followerId));
                redisTemplate.opsForValue().increment(UserRedisKeys.followersCount(followingId));
            } catch (Exception e) {
                log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
            }

            // 6. 发布事件
            eventPublisher.publish(new UserFollowedEvent(followerId, followingId));

            log.info("User followed: followerId={}, followingId={}", followerId, followingId);

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
     * 取消关注
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     */
    public void unfollow(Long followerId, Long followingId) {
        // 1. 业务验证
        followDomainService.validateUnfollow(followerId, followingId);

        // 2. 分布式锁防止并发
        String lockKey = "follow:" + followerId + ":" + followingId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCode.REQUEST_TOO_FREQUENT, "操作过于频繁，请稍后再试");
            }

            // 3. 幂等性检查
            if (!userFollowRepository.exists(followerId, followingId)) {
                log.debug("Not followed: followerId={}, followingId={}", followerId, followingId);
                return;
            }

            // 4. 数据库操作在事务中执行
            transactionTemplate.executeWithoutResult(status -> {
                userFollowRepository.delete(followerId, followingId);
                userFollowRepository.decrementFollowing(followerId);
                userFollowRepository.decrementFollowers(followingId);
            });

            // 5. 事务提交成功后，更新 Redis 缓存
            try {
                redisTemplate.opsForValue().decrement(UserRedisKeys.followingCount(followerId));
                redisTemplate.opsForValue().decrement(UserRedisKeys.followersCount(followingId));
            } catch (Exception e) {
                log.warn("Redis 更新失败，将由 CDC 或定时任务修复: {}", e.getMessage());
            }

            log.info("User unfollowed: followerId={}, followingId={}", followerId, followingId);

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
     * 获取粉丝列表
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 粉丝用户列表
     */
    public List<UserVO> getFollowers(Long userId, int page, int size) {
        // 参数验证
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        
        List<UserFollow> followers = userFollowRepository.findFollowers(userId, page, size);
        return followers.stream()
                .map(follow -> userRepository.findById(follow.getFollowerId()).orElse(null))
                .filter(user -> user != null)
                .map(UserAssembler::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取关注列表
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 关注用户列表
     */
    public List<UserVO> getFollowings(Long userId, int page, int size) {
        // 参数验证
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        
        List<UserFollow> followings = userFollowRepository.findFollowings(userId, page, size);
        return followings.stream()
                .map(follow -> userRepository.findById(follow.getFollowingId()).orElse(null))
                .filter(user -> user != null)
                .map(UserAssembler::toVO)
                .collect(Collectors.toList());
    }

    /**
     * 获取关注统计
     *
     * @param userId 用户ID
     * @param currentUserId 当前用户ID（可选）
     * @return 关注统计
     */
    public FollowStatsVO getFollowStats(Long userId, Long currentUserId) {
        // 优先从 Redis 获取
        Integer followersCount = getFollowersCount(userId);
        Integer followingCount = getFollowingCount(userId);

        FollowStatsVO statsVO = new FollowStatsVO();
        statsVO.setUserId(userId);
        statsVO.setFollowersCount(followersCount);
        statsVO.setFollowingCount(followingCount);

        // 如果有当前用户，检查关注关系
        if (currentUserId != null && !currentUserId.equals(userId)) {
            statsVO.setIsFollowing(userFollowRepository.exists(currentUserId, userId));
            statsVO.setIsFollowed(userFollowRepository.exists(userId, currentUserId));
        }

        return statsVO;
    }

    /**
     * 获取关注数（优先 Redis，降级数据库）
     */
    public Integer getFollowingCount(Long userId) {
        try {
            Object cached = redisTemplate.opsForValue().get(UserRedisKeys.followingCount(userId));
            if (cached != null) {
                return ((Number) cached).intValue();
            }
        } catch (Exception e) {
            log.warn("Redis 查询失败，降级到数据库: {}", e.getMessage());
        }

        // 从数据库查询并回填缓存
        UserFollowStats stats = userFollowRepository.findStatsByUserId(userId)
                .orElse(UserFollowStats.create(userId));
        int count = stats.getFollowingCount();

        try {
            redisTemplate.opsForValue().set(UserRedisKeys.followingCount(userId), count, 1, TimeUnit.HOURS);
        } catch (Exception ignored) {
        }

        return count;
    }

    /**
     * 获取粉丝数（优先 Redis，降级数据库）
     */
    public Integer getFollowersCount(Long userId) {
        try {
            Object cached = redisTemplate.opsForValue().get(UserRedisKeys.followersCount(userId));
            if (cached != null) {
                return ((Number) cached).intValue();
            }
        } catch (Exception e) {
            log.warn("Redis 查询失败，降级到数据库: {}", e.getMessage());
        }

        // 从数据库查询并回填缓存
        UserFollowStats stats = userFollowRepository.findStatsByUserId(userId)
                .orElse(UserFollowStats.create(userId));
        int count = stats.getFollowersCount();

        try {
            redisTemplate.opsForValue().set(UserRedisKeys.followersCount(userId), count, 1, TimeUnit.HOURS);
        } catch (Exception ignored) {
        }

        return count;
    }

    /**
     * 检查是否已关注
     */
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.exists(followerId, followingId);
    }
}
