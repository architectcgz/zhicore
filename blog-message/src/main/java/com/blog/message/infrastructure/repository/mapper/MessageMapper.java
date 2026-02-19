package com.blog.message.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.message.infrastructure.repository.po.MessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 消息Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface MessageMapper extends BaseMapper<MessagePO> {

    /**
     * 查询会话的消息列表（游标分页）
     *
     * @param conversationId 会话ID
     * @param cursor 游标（消息ID）
     * @param limit 数量限制
     * @return 消息列表
     */
    @Select("<script>" +
            "SELECT * FROM messages " +
            "WHERE conversation_id = #{conversationId} " +
            "<if test='cursor != null'>" +
            "AND id &lt; #{cursor} " +
            "</if>" +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<MessagePO> findByConversationId(@Param("conversationId") Long conversationId,
                                         @Param("cursor") Long cursor,
                                         @Param("limit") int limit);

    /**
     * 批量标记消息为已读
     *
     * @param conversationId 会话ID
     * @param receiverId 接收者ID
     */
    @Update("UPDATE messages SET is_read = true, read_at = NOW() " +
            "WHERE conversation_id = #{conversationId} " +
            "AND receiver_id = #{receiverId} " +
            "AND is_read = false")
    void markAsRead(@Param("conversationId") Long conversationId,
                    @Param("receiverId") Long receiverId);

    /**
     * 统计用户未读消息总数
     *
     * @param userId 用户ID
     * @return 未读消息数
     */
    @Select("SELECT COUNT(*) FROM messages " +
            "WHERE receiver_id = #{userId} AND is_read = false AND status = 0")
    int countUnreadByUserId(@Param("userId") Long userId);
}
