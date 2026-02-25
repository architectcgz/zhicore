package com.zhicore.message.domain.repository;

import com.zhicore.message.domain.model.Conversation;

import java.util.List;
import java.util.Optional;

/**
 * 会话仓储接口
 *
 * @author ZhiCore Team
 */
public interface ConversationRepository {

    /**
     * 保存会话
     *
     * @param conversation 会话
     */
    void save(Conversation conversation);

    /**
     * 根据ID查询会话
     *
     * @param id 会话ID
     * @return 会话
     */
    Optional<Conversation> findById(Long id);

    /**
     * 根据参与者查询会话
     * 注意：participant1Id 和 participant2Id 需要按字典序排列
     *
     * @param participant1Id 参与者1 ID（较小）
     * @param participant2Id 参与者2 ID（较大）
     * @return 会话
     */
    Optional<Conversation> findByParticipants(Long participant1Id, Long participant2Id);

    /**
     * 查询用户的会话列表（按最后消息时间倒序）
     *
     * @param userId 用户ID
     * @param cursor 游标（会话ID，为null时从最新开始）
     * @param limit 数量限制
     * @return 会话列表
     */
    List<Conversation> findByUserId(Long userId, Long cursor, int limit);

    /**
     * 更新会话
     *
     * @param conversation 会话
     */
    void update(Conversation conversation);

    /**
     * 统计用户的会话数
     *
     * @param userId 用户ID
     * @return 会话数
     */
    int countByUserId(Long userId);
}
