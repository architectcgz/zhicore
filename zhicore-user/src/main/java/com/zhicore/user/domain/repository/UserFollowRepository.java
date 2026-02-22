package com.zhicore.user.domain.repository;

import com.zhicore.user.domain.model.UserFollow;
import com.zhicore.user.domain.model.UserFollowStats;

import java.util.List;
import java.util.Optional;

/**
 * 用户关注仓储接口
 *
 * @author ZhiCore Team
 */
public interface UserFollowRepository {

    /**
     * 保存关注关系
     *
     * @param follow 关注关系
     */
    void save(UserFollow follow);

    /**
     * 删除关注关系
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     */
    void delete(Long followerId, Long followingId);

    /**
     * 检查关注关系是否存在
     *
     * @param followerId 关注者ID
     * @param followingId 被关注者ID
     * @return 是否存在
     */
    boolean exists(Long followerId, Long followingId);

    /**
     * 查询用户的粉丝列表
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 粉丝列表
     */
    List<UserFollow> findFollowers(Long userId, int page, int size);

    /**
     * 查询用户的关注列表
     *
     * @param userId 用户ID
     * @param page 页码
     * @param size 每页大小
     * @return 关注列表
     */
    List<UserFollow> findFollowings(Long userId, int page, int size);

    /**
     * 获取用户关注统计
     *
     * @param userId 用户ID
     * @return 关注统计
     */
    Optional<UserFollowStats> findStatsByUserId(Long userId);

    /**
     * 保存或更新关注统计
     *
     * @param stats 关注统计
     */
    void saveOrUpdateStats(UserFollowStats stats);

    /**
     * 增加关注数
     *
     * @param userId 用户ID
     */
    void incrementFollowing(Long userId);

    /**
     * 减少关注数
     *
     * @param userId 用户ID
     */
    void decrementFollowing(Long userId);

    /**
     * 增加粉丝数
     *
     * @param userId 用户ID
     */
    void incrementFollowers(Long userId);

    /**
     * 减少粉丝数
     *
     * @param userId 用户ID
     */
    void decrementFollowers(Long userId);
}
