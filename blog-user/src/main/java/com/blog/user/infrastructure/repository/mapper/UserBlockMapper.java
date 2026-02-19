package com.blog.user.infrastructure.repository.mapper;

import com.blog.user.infrastructure.repository.po.UserBlockPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 用户拉黑 Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface UserBlockMapper extends BaseMapper<UserBlockPO> {

    /**
     * 检查拉黑关系是否存在
     */
    @Select("SELECT EXISTS(SELECT 1 FROM user_blocks WHERE blocker_id = #{blockerId} AND blocked_id = #{blockedId})")
    boolean exists(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /**
     * 删除拉黑关系
     */
    @Delete("DELETE FROM user_blocks WHERE blocker_id = #{blockerId} AND blocked_id = #{blockedId}")
    void deleteByBlockerAndBlocked(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /**
     * 查询拉黑列表
     */
    @Select("SELECT * FROM user_blocks WHERE blocker_id = #{blockerId} ORDER BY created_at DESC LIMIT #{size} OFFSET #{offset}")
    List<UserBlockPO> selectByBlockerId(@Param("blockerId") Long blockerId, @Param("offset") int offset, @Param("size") int size);
}
