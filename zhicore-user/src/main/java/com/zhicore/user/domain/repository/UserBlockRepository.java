package com.zhicore.user.domain.repository;

import com.zhicore.user.domain.model.UserBlock;

import java.util.List;

/**
 * 用户拉黑仓储接口
 *
 * @author ZhiCore Team
 */
public interface UserBlockRepository {

    /**
     * 保存拉黑关系
     *
     * @param block 拉黑关系
     */
    void save(UserBlock block);

    /**
     * 删除拉黑关系
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     */
    void delete(Long blockerId, Long blockedId);

    /**
     * 检查拉黑关系是否存在
     *
     * @param blockerId 拉黑者ID
     * @param blockedId 被拉黑者ID
     * @return 是否存在
     */
    boolean exists(Long blockerId, Long blockedId);

    /**
     * 查询用户的拉黑列表
     *
     * @param blockerId 拉黑者ID
     * @param page 页码
     * @param size 每页大小
     * @return 拉黑列表
     */
    List<UserBlock> findByBlockerId(Long blockerId, int page, int size);
}
