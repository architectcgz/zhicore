package com.zhicore.user.domain.repository;

import com.zhicore.user.domain.model.UserFollow;
import com.zhicore.user.domain.model.UserFollowStats;

import java.time.OffsetDateTime;
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
     * 按稳定游标查询粉丝列表。
     *
     * @param userId 被关注用户 ID
     * @param afterCreatedAt 上一页最后一条关注时间
     * @param afterFollowerId 上一页最后一条粉丝 ID
     * @param limit 每页数量
     * @return 粉丝列表
     */
    List<UserFollow> findFollowersByCursor(Long userId, OffsetDateTime afterCreatedAt, Long afterFollowerId, int limit);

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
     * 按 followerId 游标稳定查询粉丝分片。
     *
     * @param userId 用户ID
     * @param cursorFollowerId 起始游标，返回 followerId 大于该值的数据
     * @param size 分片大小
     * @return 粉丝分片
     */
    List<UserFollow> findFollowerShard(Long userId, Long cursorFollowerId, int size);

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
