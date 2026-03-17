package com.zhicore.message.application.service.query;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.message.application.model.DirectConversationRef;
import com.zhicore.message.application.model.ImConversationView;
import com.zhicore.message.application.port.im.ImConversationQueryGateway;
import com.zhicore.message.application.port.user.UserMessagingPort;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.ConversationVO;
import com.zhicore.message.application.sentinel.MessageSentinelHandlers;
import com.zhicore.message.application.sentinel.MessageSentinelResources;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话读服务。
 *
 * 负责会话列表与详情查询，不承载写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationQueryService {

    private static final String USER_SERVICE_DEGRADED_MESSAGE = "用户服务已降级";

    private final ConversationRepository conversationRepository;
    private final MessageDomainService messageDomainService;
    private final UserMessagingPort userMessagingPort;
    private final ImConversationQueryGateway imConversationQueryGateway;

    /**
     * 获取会话列表（按最后消息时间排序）
     *
     * @param cursor 游标（会话ID）
     * @param limit 数量限制
     * @return 会话列表
     */
    @SentinelResource(
            value = MessageSentinelResources.GET_CONVERSATION_LIST,
            blockHandlerClass = MessageSentinelHandlers.class,
            blockHandler = "handleConversationListBlocked"
    )
    public List<ConversationVO> getConversationList(Long cursor, int limit) {
        Long userId = UserContext.getUserId();

        List<ImConversationView> conversations = imConversationQueryGateway.listConversations(userId, cursor, limit);
        if (conversations.isEmpty()) {
            return new ArrayList<>();
        }

        Set<Long> otherUserIds = conversations.stream()
                .map(c -> c.getConversationRef().otherParticipant(userId))
                .collect(Collectors.toSet());

        Map<Long, UserSimpleDTO> userMap = fetchUserInfoBatch(otherUserIds);

        return conversations.stream()
                .map(c -> toConversationVO(c, userId, userMap))
                .collect(Collectors.toList());
    }

    /**
     * 获取会话详情
     *
     * @param conversationId 会话ID
     * @return 会话视图对象
     */
    @SentinelResource(
            value = MessageSentinelResources.GET_CONVERSATION,
            blockHandlerClass = MessageSentinelHandlers.class,
            blockHandler = "handleConversationBlocked"
    )
    public ConversationVO getConversation(Long conversationId) {
        Long userId = UserContext.getUserId();
        
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException(ResultCode.CONVERSATION_NOT_FOUND));
        messageDomainService.validateConversationAccess(conversation, userId);

        DirectConversationRef conversationRef = DirectConversationRef.of(
                conversation.getParticipant1Id(),
                conversation.getParticipant2Id()
        );
        ImConversationView conversationView = imConversationQueryGateway.findConversation(userId, conversationRef)
                .orElseGet(() -> toFallbackView(conversation, userId));

        Long otherUserId = conversationView.getConversationRef().otherParticipant(userId);
        UserSimpleDTO otherUser = fetchUserInfo(otherUserId);

        return toConversationVO(conversationView, userId, otherUser);
    }

    /**
     * 根据对方用户ID获取会话
     *
     * @param otherUserId 对方用户ID
     * @return 会话视图对象（如果不存在返回null）
     */
    @SentinelResource(
            value = MessageSentinelResources.GET_CONVERSATION_BY_USER,
            blockHandlerClass = MessageSentinelHandlers.class,
            blockHandler = "handleConversationByUserBlocked"
    )
    public ConversationVO getConversationByUser(Long otherUserId) {
        Long userId = UserContext.getUserId();

        DirectConversationRef conversationRef = DirectConversationRef.of(userId, otherUserId);
        return imConversationQueryGateway.findConversation(userId, conversationRef)
                .map(conversation -> {
                    UserSimpleDTO otherUser = fetchUserInfo(otherUserId);
                    return toConversationVO(conversation, userId, otherUser);
                })
                .orElse(null);
    }

    /**
     * 获取会话数量
     *
     * @return 会话数量
     */
    @SentinelResource(
            value = MessageSentinelResources.GET_CONVERSATION_COUNT,
            blockHandlerClass = MessageSentinelHandlers.class,
            blockHandler = "handleConversationCountBlocked"
    )
    public int getConversationCount() {
        Long userId = UserContext.getUserId();
        return imConversationQueryGateway.countConversations(userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 批量获取用户信息
     */
    private Map<Long, UserSimpleDTO> fetchUserInfoBatch(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        return userIds.stream()
                .map(id -> Map.entry(id, fetchUserInfo(id)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 获取单个用户信息
     */
    private UserSimpleDTO fetchUserInfo(Long userId) {
        try {
            return userMessagingPort.getUserSimple(userId);
        } catch (Exception e) {
            log.warn("Failed to fetch user info: userId={}", userId, e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, USER_SERVICE_DEGRADED_MESSAGE);
        }
    }

    /**
     * 转换为会话视图对象
     */
    private ConversationVO toConversationVO(ImConversationView conversation, Long userId,
                                            Map<Long, UserSimpleDTO> userMap) {
        Long otherUserId = conversation.getConversationRef().otherParticipant(userId);
        UserSimpleDTO otherUser = userMap.get(otherUserId);
        return toConversationVO(conversation, userId, otherUser);
    }

    /**
     * 转换为会话视图对象
     */
    private ConversationVO toConversationVO(ImConversationView conversation, Long userId,
                                            UserSimpleDTO otherUser) {
        Long otherUserId = conversation.getConversationRef().otherParticipant(userId);

        return ConversationVO.builder()
                .id(resolveLocalConversationId(conversation))
                .otherUserId(otherUserId)
                .otherUserNickName(otherUser != null ? otherUser.getNickname() : null)
                .otherUserAvatarUrl(otherUser != null ? otherUser.getAvatarId() : null)
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(conversation.getUnreadCount())
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private ImConversationView toFallbackView(Conversation conversation, Long userId) {
        return ImConversationView.builder()
                .localConversationId(conversation.getId())
                .conversationRef(DirectConversationRef.of(
                        conversation.getParticipant1Id(),
                        conversation.getParticipant2Id()
                ))
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(conversation.getUnreadCount(userId))
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    private Long resolveLocalConversationId(ImConversationView conversationView) {
        if (conversationView.getLocalConversationId() != null) {
            return conversationView.getLocalConversationId();
        }
        return conversationRepository.findByParticipants(
                        conversationView.getConversationRef().participant1Id(),
                        conversationView.getConversationRef().participant2Id()
                )
                .map(Conversation::getId)
                .orElse(null);
    }
}
