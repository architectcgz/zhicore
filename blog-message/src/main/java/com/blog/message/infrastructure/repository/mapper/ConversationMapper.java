package com.blog.message.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.message.infrastructure.repository.po.ConversationPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface ConversationMapper extends BaseMapper<ConversationPO> {

    /**
     * 根据参与者查询会话
     *
     * @param participant1Id 参与者1 ID
     * @param participant2Id 参与者2 ID
     * @return 会话
     */
    @Select("SELECT * FROM conversations " +
            "WHERE participant1_id = #{participant1Id} " +
            "AND participant2_id = #{participant2Id}")
    ConversationPO findByParticipants(@Param("participant1Id") Long participant1Id,
                                      @Param("participant2Id") Long participant2Id);

    /**
     * 查询用户的会话列表（游标分页）
     *
     * @param userId 用户ID
     * @param cursor 游标（会话ID）
     * @param limit 数量限制
     * @return 会话列表
     */
    @Select("<script>" +
            "SELECT * FROM conversations " +
            "WHERE (participant1_id = #{userId} OR participant2_id = #{userId}) " +
            "<if test='cursor != null'>" +
            "AND (last_message_at, id) &lt; " +
            "(SELECT last_message_at, id FROM conversations WHERE id = #{cursor}) " +
            "</if>" +
            "ORDER BY last_message_at DESC NULLS LAST, id DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<ConversationPO> findByUserId(@Param("userId") Long userId,
                                      @Param("cursor") Long cursor,
                                      @Param("limit") int limit);

    /**
     * 统计用户的会话数
     *
     * @param userId 用户ID
     * @return 会话数
     */
    @Select("SELECT COUNT(*) FROM conversations " +
            "WHERE participant1_id = #{userId} OR participant2_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);
}
