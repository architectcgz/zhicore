package com.blog.message.infrastructure.repository;

import com.blog.common.util.DateTimeUtils;
import com.blog.message.domain.model.Message;
import com.blog.message.domain.model.MessageStatus;
import com.blog.message.domain.model.MessageType;
import com.blog.message.domain.repository.MessageRepository;
import com.blog.message.infrastructure.repository.mapper.MessageMapper;
import com.blog.message.infrastructure.repository.po.MessagePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 消息仓储实现
 *
 * @author Blog Team
 */
@Repository
@RequiredArgsConstructor
public class MessageRepositoryImpl implements MessageRepository {

    private final MessageMapper messageMapper;

    @Override
    public void save(Message message) {
        MessagePO po = toPO(message);
        messageMapper.insert(po);
    }

    @Override
    public Optional<Message> findById(Long id) {
        MessagePO po = messageMapper.selectById(id);
        return Optional.ofNullable(po).map(this::toDomain);
    }

    @Override
    public List<Message> findByConversationId(Long conversationId, Long cursor, int limit) {
        List<MessagePO> poList = messageMapper.findByConversationId(conversationId, cursor, limit);
        return poList.stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public void markAsRead(Long conversationId, Long receiverId) {
        messageMapper.markAsRead(conversationId, receiverId);
    }

    @Override
    public void update(Message message) {
        MessagePO po = toPO(message);
        messageMapper.updateById(po);
    }

    @Override
    public int countUnreadByUserId(Long userId) {
        return messageMapper.countUnreadByUserId(userId);
    }

    // ==================== 转换方法 ====================

    private MessagePO toPO(Message message) {
        MessagePO po = new MessagePO();
        po.setId(message.getId());
        po.setConversationId(message.getConversationId());
        po.setSenderId(message.getSenderId());
        po.setReceiverId(message.getReceiverId());
        po.setType(message.getType().getCode());
        po.setContent(message.getContent());
        po.setMediaUrl(message.getMediaUrl());
        po.setIsRead(message.isRead());
        po.setReadAt(DateTimeUtils.toOffsetDateTime(message.getReadAt()));
        po.setStatus(message.getStatus().getCode());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(message.getCreatedAt()));
        return po;
    }

    private Message toDomain(MessagePO po) {
        return Message.reconstitute(
                po.getId(),
                po.getConversationId(),
                po.getSenderId(),
                po.getReceiverId(),
                MessageType.fromCode(po.getType()),
                po.getContent(),
                po.getMediaUrl(),
                po.getIsRead() != null && po.getIsRead(),
                DateTimeUtils.toLocalDateTime(po.getReadAt()),
                MessageStatus.fromCode(po.getStatus()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt())
        );
    }
}
