package com.zhicore.message.domain.repository;

import com.zhicore.message.domain.model.Message;

import java.util.List;
import java.util.Optional;

/**
 * 消息仓储接口
 *
 * @author ZhiCore Team
 */
public interface MessageRepository {

    /**
     * 保存消息
     *
     * @param message 消息
     */
    void save(Message message);

    /**
     * 根据ID查询消息
     *
     * @param id 消息ID
     * @return 消息
     */
    Optional<Message> findById(Long id);

    /**
     * 查询会话的消息列表（分页，按时间倒序）
     *
     * @param conversationId 会话ID
     * @param cursor 游标（消息ID，为null时从最新开始）
     * @param limit 数量限制
     * @return 消息列表
     */
    List<Message> findByConversationId(Long conversationId, Long cursor, int limit);

    /**
     * 批量标记消息为已读
     *
     * @param conversationId 会话ID
     * @param receiverId 接收者ID
     */
    void markAsRead(Long conversationId, Long receiverId);

    /**
     * 更新消息
     *
     * @param message 消息
     */
    void update(Message message);

    /**
     * 统计用户未读消息总数
     *
     * @param userId 用户ID
     * @return 未读消息数
     */
    int countUnreadByUserId(Long userId);
}
