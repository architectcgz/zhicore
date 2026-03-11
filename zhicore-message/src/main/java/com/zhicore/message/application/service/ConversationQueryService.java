package com.zhicore.message.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.UserMessagingClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import com.zhicore.message.application.dto.ConversationVO;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import com.zhicore.message.application.sentinel.MessageSentinelHandlers;
import com.zhicore.message.application.sentinel.MessageSentinelResources;
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
    private final UserMessagingClient userServiceClient;

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
        
        // 查询会话列表
        List<Conversation> conversations = conversationRepository.findByUserId(userId, cursor, limit);
        
        if (conversations.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 收集对方用户ID
        Set<Long> otherUserIds = conversations.stream()
                .map(c -> c.getOtherParticipant(userId))
                .collect(Collectors.toSet());
        
        // 批量获取用户信息
        Map<Long, UserSimpleDTO> userMap = fetchUserInfoBatch(otherUserIds);
        
        // 转换为视图对象
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
        
        // 验证访问权限
        messageDomainService.validateConversationAccess(conversation, userId);
        
        // 获取对方用户信息
        Long otherUserId = conversation.getOtherParticipant(userId);
        UserSimpleDTO otherUser = fetchUserInfo(otherUserId);
        
        return toConversationVO(conversation, userId, otherUser);
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
        
        // 规范化参与者ID
        Long[] participants = Conversation.normalizeParticipants(userId, otherUserId);
        
        return conversationRepository.findByParticipants(participants[0], participants[1])
                .map(c -> {
                    UserSimpleDTO otherUser = fetchUserInfo(otherUserId);
                    return toConversationVO(c, userId, otherUser);
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
        return conversationRepository.countByUserId(userId);
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
            ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(userId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            }
        } catch (Exception e) {
            log.warn("Failed to fetch user info: userId={}", userId, e);
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, USER_SERVICE_DEGRADED_MESSAGE);
        }
        throw new BusinessException(ResultCode.SERVICE_DEGRADED, USER_SERVICE_DEGRADED_MESSAGE);
    }

    /**
     * 转换为会话视图对象
     */
    private ConversationVO toConversationVO(Conversation conversation, Long userId, 
                                            Map<Long, UserSimpleDTO> userMap) {
        Long otherUserId = conversation.getOtherParticipant(userId);
        UserSimpleDTO otherUser = userMap.get(otherUserId);
        return toConversationVO(conversation, userId, otherUser);
    }

    /**
     * 转换为会话视图对象
     */
    private ConversationVO toConversationVO(Conversation conversation, Long userId, 
                                            UserSimpleDTO otherUser) {
        Long otherUserId = conversation.getOtherParticipant(userId);
        
        return ConversationVO.builder()
                .id(conversation.getId())
                .otherUserId(otherUserId)
                .otherUserNickName(otherUser != null ? otherUser.getNickname() : null)
                .otherUserAvatarUrl(otherUser != null ? otherUser.getAvatarId() : null)
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(conversation.getUnreadCount(userId))
                .createdAt(conversation.getCreatedAt())
                .build();
    }
}
