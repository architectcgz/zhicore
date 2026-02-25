package com.zhicore.user.infrastructure.repository.mapper;

import com.zhicore.user.infrastructure.repository.po.UserFollowPO;
import com.zhicore.user.infrastructure.repository.po.UserFollowStatsPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户关注 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollowPO> {

    /**
     * 检查关注关系是否存在
     */
    @Select("SELECT EXISTS(SELECT 1 FROM user_follows WHERE follower_id = #{followerId} AND following_id = #{followingId})")
    boolean exists(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    /**
     * 删除关注关系
     */
    @Delete("DELETE FROM user_follows WHERE follower_id = #{followerId} AND following_id = #{followingId}")
    void deleteByFollowerAndFollowing(@Param("followerId") Long followerId, @Param("followingId") Long followingId);

    /**
     * 查询粉丝列表
     */
    @Select("SELECT * FROM user_follows WHERE following_id = #{userId} ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<UserFollowPO> selectFollowers(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    /**
     * 查询关注列表
     */
    @Select("SELECT * FROM user_follows WHERE follower_id = #{userId} ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<UserFollowPO> selectFollowings(@Param("userId") Long userId, @Param("offset") int offset, @Param("size") int size);

    /**
     * 查询关注统计
     */
    @Select("SELECT * FROM user_follow_stats WHERE user_id = #{userId}")
    UserFollowStatsPO selectStatsByUserId(@Param("userId") Long userId);

    /**
     * 插入关注统计
     */
    @Insert("INSERT INTO user_follow_stats (user_id, followers_count, following_count) VALUES (#{userId}, #{followersCount}, #{followingCount})")
    void insertStats(UserFollowStatsPO stats);

    /**
     * 更新关注统计
     */
    @Update("UPDATE user_follow_stats SET followers_count = #{followersCount}, following_count = #{followingCount} WHERE user_id = #{userId}")
    void updateStats(UserFollowStatsPO stats);

    /**
     * 增加关注数
     */
    @Update("INSERT INTO user_follow_stats (user_id, following_count) VALUES (#{userId}, 1) " +
            "ON CONFLICT (user_id) DO UPDATE SET following_count = user_follow_stats.following_count + 1")
    void incrementFollowing(@Param("userId") Long userId);

    /**
     * 减少关注数
     */
    @Update("UPDATE user_follow_stats SET following_count = GREATEST(0, following_count - 1) WHERE user_id = #{userId}")
    void decrementFollowing(@Param("userId") Long userId);

    /**
     * 增加粉丝数
     */
    @Update("INSERT INTO user_follow_stats (user_id, followers_count) VALUES (#{userId}, 1) " +
            "ON CONFLICT (user_id) DO UPDATE SET followers_count = user_follow_stats.followers_count + 1")
    void incrementFollowers(@Param("userId") Long userId);

    /**
     * 减少粉丝数
     */
    @Update("UPDATE user_follow_stats SET followers_count = GREATEST(0, followers_count - 1) WHERE user_id = #{userId}")
    void decrementFollowers(@Param("userId") Long userId);
}
