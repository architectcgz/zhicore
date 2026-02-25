package com.zhicore.message.application.service;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.message.application.dto.ConversationVO;
import com.zhicore.message.domain.model.Conversation;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.service.MessageDomainService;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 会话应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationApplicationService {

    private final ConversationRepository conversationRepository;
    private final MessageDomainService messageDomainService;
    private final UserServiceClient userServiceClient;

    /**
     * 获取会话列表（按最后消息时间排序）
     *
     * @param cursor 游标（会话ID）
     * @param limit 数量限制
     * @return 会话列表
     */
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
    public ConversationVO getConversation(Long conversationId) {
        Long userId = UserContext.getUserId();
        
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        
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
    public int getConversationCount() {
        Long userId = UserContext.getUserId();
        return conversationRepository.countByUserId(userId);
    }

    // ==================== 私有方法 ====================

    /**
     * 批量获取用户信息
     */
    private Map<Long, UserSimpleDTO> fetchUserInfoBatch(Set<Long> userIds) {
        // 这里简化处理，实际应该使用批量接口
        return userIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        this::fetchUserInfo,
                        (a, b) -> a
                ));
    }

    /**
     * 获取单个用户信息
     */
    private UserSimpleDTO fetchUserInfo(Long userId) {
        ApiResponse<UserSimpleDTO> response = userServiceClient.getUserSimple(String.valueOf(userId));
        if (response.isSuccess() && response.getData() != null) {
            return response.getData();
        }
        // 降级处理：返回默认用户信息
        UserSimpleDTO defaultUser = new UserSimpleDTO();
        defaultUser.setId(userId);
        defaultUser.setNickName("用户" + userId);
        return defaultUser;
    }

    /**
     * 转换为会话视图对象
     */
    private ConversationVO toConversationVO(Conversation conversation, Long userId, 
                                            Map<Long, UserSimpleDTO> userMap) {
        Long otherUserId = conversation.getOtherParticipant(userId);
        UserSimpleDTO otherUser = userMap.getOrDefault(otherUserId, new UserSimpleDTO());
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
                .otherUserNickName(otherUser != null ? otherUser.getNickName() : null)
                .otherUserAvatarUrl(otherUser != null ? otherUser.getAvatarUrl() : null)
                .lastMessageContent(conversation.getLastMessageContent())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(conversation.getUnreadCount(userId))
                .createdAt(conversation.getCreatedAt())
                .build();
    }
}
