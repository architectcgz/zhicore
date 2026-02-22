package com.zhicore.message.infrastructure.repository;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.infrastructure.repository.mapper.ConversationMapper;
import com.zhicore.message.infrastructure.repository.po.ConversationPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 会话仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class ConversationRepositoryImpl implements ConversationRepository {

    private final ConversationMapper conversationMapper;

    @Override
    public void save(Conversation conversation) {
        ConversationPO po = toPO(conversation);
        conversationMapper.insert(po);
    }

    @Override
    public Optional<Conversation> findById(Long id) {
        ConversationPO po = conversationMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public Optional<Conversation> findByParticipants(Long participant1Id, Long participant2Id) {
        ConversationPO po = conversationMapper.findByParticipants(participant1Id, participant2Id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<Conversation> findByUserId(Long userId, Long cursor, int limit) {
        List<ConversationPO> poList = conversationMapper.findByUserId(userId, cursor, limit);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void update(Conversation conversation) {
        ConversationPO po = toPO(conversation);
        conversationMapper.updateById(po);
    }

    @Override
    public int countByUserId(Long userId) {
        return conversationMapper.countByUserId(userId);
    }

    // ==================== 转换方法 ====================

    private ConversationPO toPO(Conversation conversation) {
        ConversationPO po = new ConversationPO();
        po.setId(conversation.getId());
        po.setParticipant1Id(conversation.getParticipant1Id());
        po.setParticipant2Id(conversation.getParticipant2Id());
        po.setLastMessageId(conversation.getLastMessageId());
        po.setLastMessageContent(conversation.getLastMessageContent());
        po.setLastMessageAt(DateTimeUtils.toOffsetDateTime(conversation.getLastMessageAt()));
        po.setUnreadCount1(conversation.getUnreadCount1());
        po.setUnreadCount2(conversation.getUnreadCount2());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(conversation.getCreatedAt()));
        return po;
    }

    private Conversation toDomain(ConversationPO po) {
        return Conversation.reconstitute(
                po.getId(),
                po.getParticipant1Id(),
                po.getParticipant2Id(),
                po.getLastMessageId(),
                po.getLastMessageContent(),
                DateTimeUtils.toLocalDateTime(po.getLastMessageAt()),
                po.getUnreadCount1() != null ? po.getUnreadCount1() : 0,
                po.getUnreadCount2() != null ? po.getUnreadCount2() : 0,
                DateTimeUtils.toLocalDateTime(po.getCreatedAt())
        );
    }
}
