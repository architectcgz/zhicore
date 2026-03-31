package com.zhicore.user.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.dto.user.FollowerCursorItemDTO;
import com.zhicore.api.dto.user.FollowerCursorPageDTO;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.dto.FollowStatsVO;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.store.FollowStatsStore;
import com.zhicore.user.application.sentinel.UserSentinelHandlers;
import com.zhicore.user.application.sentinel.UserSentinelResources;
import com.zhicore.user.domain.model.UserFollow;
import com.zhicore.user.domain.model.UserFollowStats;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserFollowRepository;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_CURSOR_LIMIT = 200;
    private static final int MAX_CURSOR_LIMIT = 500;
    private static final Duration FOLLOW_STATS_CACHE_TTL = Duration.ofHours(1);

    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;
    private final FollowStatsStore followStatsStore;

    @SentinelResource(
            value = UserSentinelResources.GET_FOLLOWERS,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetFollowersBlocked"
    )
    public List<UserVO> getFollowers(Long userId, int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);

        List<UserFollow> followers = userFollowRepository.findFollowers(userId, page, size);
        return mapUsersInOrder(
                followers.stream().map(UserFollow::getFollowerId).collect(Collectors.toCollection(LinkedHashSet::new)),
                followers.stream().map(UserFollow::getFollowerId).toList()
        );
    }

    public FollowerCursorPageDTO getFollowersByCursor(Long userId,
                                                      OffsetDateTime afterCreatedAt,
                                                      Long afterFollowerId,
                                                      Integer limit) {
        int normalizedLimit = normalizeCursorLimit(limit);
        List<UserFollow> followers = userFollowRepository.findFollowersByCursor(
                userId, afterCreatedAt, afterFollowerId, normalizedLimit);

        if (followers.isEmpty()) {
            return FollowerCursorPageDTO.builder()
                    .items(List.of())
                    .nextAfterCreatedAt(null)
                    .nextAfterFollowerId(null)
                    .hasMore(false)
                    .build();
        }

        UserFollow last = followers.get(followers.size() - 1);
        return FollowerCursorPageDTO.builder()
                .items(followers.stream()
                        .map(follow -> FollowerCursorItemDTO.builder()
                                .followerId(follow.getFollowerId())
                                .followedAt(follow.getCreatedAt())
                                .build())
                        .toList())
                .nextAfterCreatedAt(last.getCreatedAt())
                .nextAfterFollowerId(last.getFollowerId())
                .hasMore(followers.size() == normalizedLimit)
                .build();
    }

    @SentinelResource(
            value = UserSentinelResources.GET_FOLLOWINGS,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetFollowingsBlocked"
    )
    public List<UserVO> getFollowings(Long userId, int page, int size) {
        page = normalizePage(page);
        size = normalizeSize(size);

        List<UserFollow> followings = userFollowRepository.findFollowings(userId, page, size);
        return mapUsersInOrder(
                followings.stream().map(UserFollow::getFollowingId).collect(Collectors.toCollection(LinkedHashSet::new)),
                followings.stream().map(UserFollow::getFollowingId).toList()
        );
    }

    @SentinelResource(
            value = UserSentinelResources.GET_FOLLOW_STATS,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleGetFollowStatsBlocked"
    )
    public FollowStatsVO getFollowStats(Long userId, Long currentUserId) {
        Integer followersCount = getFollowersCount(userId);
        Integer followingCount = getFollowingCount(userId);

        FollowStatsVO statsVO = new FollowStatsVO();
        statsVO.setUserId(userId);
        statsVO.setFollowersCount(followersCount);
        statsVO.setFollowingCount(followingCount);

        if (currentUserId != null && !currentUserId.equals(userId)) {
            statsVO.setIsFollowing(userFollowRepository.exists(currentUserId, userId));
            statsVO.setIsFollowed(userFollowRepository.exists(userId, currentUserId));
        }

        return statsVO;
    }

    public Integer getFollowingCount(Long userId) {
        try {
            Integer cached = followStatsStore.getFollowingCount(userId);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis 查询失败，降级到数据库: {}", e.getMessage());
        }

        UserFollowStats stats = userFollowRepository.findStatsByUserId(userId)
                .orElse(UserFollowStats.create(userId));
        int count = stats.getFollowingCount();

        try {
            followStatsStore.cacheFollowingCount(userId, count, FOLLOW_STATS_CACHE_TTL);
        } catch (Exception ignored) {
        }

        return count;
    }

    public Integer getFollowersCount(Long userId) {
        try {
            Integer cached = followStatsStore.getFollowersCount(userId);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis 查询失败，降级到数据库: {}", e.getMessage());
        }

        UserFollowStats stats = userFollowRepository.findStatsByUserId(userId)
                .orElse(UserFollowStats.create(userId));
        int count = stats.getFollowersCount();

        try {
            followStatsStore.cacheFollowersCount(userId, count, FOLLOW_STATS_CACHE_TTL);
        } catch (Exception ignored) {
        }

        return count;
    }

    @SentinelResource(
            value = UserSentinelResources.IS_FOLLOWING,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleIsFollowingBlocked"
    )
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowRepository.exists(followerId, followingId);
    }

    private int normalizePage(int page) {
        return page < 1 ? 1 : page;
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private int normalizeCursorLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_CURSOR_LIMIT;
        }
        return Math.min(limit, MAX_CURSOR_LIMIT);
    }

    private List<UserVO> mapUsersInOrder(Set<Long> userIds, List<Long> orderedUserIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        Map<Long, User> usersById = userRepository.findByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));
        return orderedUserIds.stream()
                .map(usersById::get)
                .filter(user -> user != null)
                .map(UserAssembler::toVO)
                .collect(Collectors.toList());
    }
}
