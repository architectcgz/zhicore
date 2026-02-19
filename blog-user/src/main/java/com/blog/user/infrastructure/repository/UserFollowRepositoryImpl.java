package com.blog.user.infrastructure.repository;

import com.blog.common.util.DateTimeUtils;
import com.blog.user.domain.model.UserFollow;
import com.blog.user.domain.model.UserFollowStats;
import com.blog.user.domain.repository.UserFollowRepository;
import com.blog.user.infrastructure.repository.mapper.UserFollowMapper;
import com.blog.user.infrastructure.repository.po.UserFollowPO;
import com.blog.user.infrastructure.repository.po.UserFollowStatsPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 用户关注仓储实现
 *
 * @author Blog Team
 */
@Repository
@RequiredArgsConstructor
public class UserFollowRepositoryImpl implements UserFollowRepository {

    private final UserFollowMapper userFollowMapper;

    @Override
    public void save(UserFollow follow) {
        UserFollowPO po = toPO(follow);
        userFollowMapper.insert(po);
    }

    @Override
    public void delete(Long followerId, Long followingId) {
        userFollowMapper.deleteByFollowerAndFollowing(followerId, followingId);
    }

    @Override
    public boolean exists(Long followerId, Long followingId) {
        return userFollowMapper.exists(followerId, followingId);
    }

    @Override
    public List<UserFollow> findFollowers(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<UserFollowPO> poList = userFollowMapper.selectFollowers(userId, offset, size);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserFollow> findFollowings(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<UserFollowPO> poList = userFollowMapper.selectFollowings(userId, offset, size);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<UserFollowStats> findStatsByUserId(Long userId) {
        UserFollowStatsPO po = userFollowMapper.selectStatsByUserId(userId);
        return Optional.ofNullable(toStatsDomain(po));
    }

    @Override
    public void saveOrUpdateStats(UserFollowStats stats) {
        UserFollowStatsPO existing = userFollowMapper.selectStatsByUserId(stats.getUserId());
        UserFollowStatsPO po = toStatsPO(stats);
        if (existing == null) {
            userFollowMapper.insertStats(po);
        } else {
            userFollowMapper.updateStats(po);
        }
    }

    @Override
    public void incrementFollowing(Long userId) {
        userFollowMapper.incrementFollowing(userId);
    }

    @Override
    public void decrementFollowing(Long userId) {
        userFollowMapper.decrementFollowing(userId);
    }

    @Override
    public void incrementFollowers(Long userId) {
        userFollowMapper.incrementFollowers(userId);
    }

    @Override
    public void decrementFollowers(Long userId) {
        userFollowMapper.decrementFollowers(userId);
    }

    private UserFollow toDomain(UserFollowPO po) {
        if (po == null) {
            return null;
        }
        return UserFollow.reconstitute(
                po.getFollowerId(),
                po.getFollowingId(),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt())
        );
    }

    private UserFollowPO toPO(UserFollow follow) {
        UserFollowPO po = new UserFollowPO();
        po.setFollowerId(follow.getFollowerId());
        po.setFollowingId(follow.getFollowingId());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(follow.getCreatedAt()));
        return po;
    }

    private UserFollowStats toStatsDomain(UserFollowStatsPO po) {
        if (po == null) {
            return null;
        }
        return UserFollowStats.reconstitute(
                po.getUserId(),
                po.getFollowersCount() != null ? po.getFollowersCount() : 0,
                po.getFollowingCount() != null ? po.getFollowingCount() : 0
        );
    }

    private UserFollowStatsPO toStatsPO(UserFollowStats stats) {
        UserFollowStatsPO po = new UserFollowStatsPO();
        po.setUserId(stats.getUserId());
        po.setFollowersCount(stats.getFollowersCount());
        po.setFollowingCount(stats.getFollowingCount());
        return po;
    }
}
