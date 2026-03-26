package com.zhicore.notification.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.api.client.UserBatchSimpleClient;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.port.policy.NotificationAggregationPolicy;
import com.zhicore.notification.application.port.store.NotificationAggregationStore;
import com.zhicore.notification.domain.model.NotificationGroupState;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.repository.NotificationGroupStateRepository;
import com.zhicore.notification.domain.repository.NotificationRepository;
import com.zhicore.notification.application.sentinel.NotificationSentinelHandlers;
import com.zhicore.notification.application.sentinel.NotificationSentinelResources;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通知聚合服务
 * 
 * 实现通知的聚合查询功能：
 * 1. 在数据库层面先按 (type, target_type, target_id) 分组聚合
 * 2. 对聚合后的结果进行分页
 * 3. 每个聚合组只返回最新的代表通知 + 聚合统计信息
 * 4. 生成聚合文案（如"张三等5人赞了你的文章"）
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationAggregationService {

    private static final String USER_SERVICE_DEGRADED_MESSAGE = "用户服务已降级";

    private final NotificationRepository notificationRepository;
    private final NotificationGroupStateRepository notificationGroupStateRepository;
    private final UserBatchSimpleClient userServiceClient;
    private final NotificationAggregationStore notificationAggregationStore;
    private final NotificationAggregationPolicy aggregationPolicy;

    /**
     * 获取聚合通知列表（带缓存）
     *
     * @param userId 用户ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 聚合后的通知列表
     */
    @SentinelResource(
            value = NotificationSentinelResources.GET_AGGREGATED_NOTIFICATIONS,
            blockHandlerClass = NotificationSentinelHandlers.class,
            blockHandler = "handleAggregatedNotificationsBlocked"
    )
    public PageResult<AggregatedNotificationVO> getAggregatedNotifications(
            Long userId, int page, int size) {

        // 1. 尝试从缓存获取
        PageResult<AggregatedNotificationVO> cached = getCachedResult(userId, page, size);
        if (cached != null) {
            log.debug("从缓存获取聚合通知: userId={}, page={}, size={}", userId, page, size);
            return cached;
        }

        ProjectionResult projectionResult = loadFromGroupState(userId, page, size);
        if (projectionResult != null) {
            return buildAndCacheResult(userId, page, size,
                    projectionResult.aggregatedList(), projectionResult.totalGroups());
        }

        // 2. 查询聚合后的通知（数据库层面已完成聚合）
        List<AggregatedNotificationDTO> aggregatedList = notificationRepository
                .findAggregatedNotifications(String.valueOf(userId), page, size);

        if (aggregatedList.isEmpty()) {
            PageResult<AggregatedNotificationVO> emptyResult = PageResult.of(
                    page, size, 0, Collections.emptyList());
            cacheResult(userId, page, size, emptyResult);
            return emptyResult;
        }

        int totalGroups = notificationRepository.countAggregatedGroups(String.valueOf(userId));
        return buildAndCacheResult(userId, page, size, aggregatedList, totalGroups);
    }

    private PageResult<AggregatedNotificationVO> buildAndCacheResult(Long userId,
                                                                     int page,
                                                                     int size,
                                                                     List<AggregatedNotificationDTO> aggregatedList,
                                                                     int totalGroups) {
        // 3. 批量获取用户信息（避免 N+1）
        Set<String> allActorIds = aggregatedList.stream()
                .filter(dto -> dto.getActorIds() != null)
                .flatMap(dto -> dto.getActorIds().stream())
                .collect(Collectors.toSet());
        
        Map<String, UserSimpleDTO> userMap = batchGetUsers(allActorIds);

        // 4. 组装 VO
        List<AggregatedNotificationVO> voList = aggregatedList.stream()
                .map(dto -> buildAggregatedVO(dto, userMap))
                .collect(Collectors.toList());

        PageResult<AggregatedNotificationVO> result = PageResult.of(page, size, totalGroups, voList);

        // 6. 缓存结果
        cacheResult(userId, page, size, result);

        log.debug("查询聚合通知: userId={}, page={}, size={}, total={}", 
                userId, page, size, totalGroups);

        return result;
    }

    private ProjectionResult loadFromGroupState(Long userId, int page, int size) {
        try {
            List<NotificationGroupState> groupStates = notificationGroupStateRepository.findPage(
                    userId, page, size, aggregationPolicy.maxRecentActors());
            if (groupStates == null || groupStates.isEmpty()) {
                return null;
            }
            int totalGroups = notificationGroupStateRepository.countByRecipientId(userId);
            List<AggregatedNotificationDTO> aggregatedList = groupStates.stream()
                    .map(this::toAggregatedNotification)
                    .collect(Collectors.toList());
            return new ProjectionResult(aggregatedList, totalGroups);
        } catch (Exception e) {
            log.warn("读取通知组状态失败，回退明细聚合: userId={}, page={}, size={}, error={}",
                    userId, page, size, e.getMessage());
            return null;
        }
    }

    /**
     * 清除用户的通知缓存（新通知到达时调用）
     *
     * @param userId 用户ID
     */
    public void invalidateCache(Long userId) {
        try {
            notificationAggregationStore.evictUser(userId);
            log.debug("清除通知缓存: userId={}", userId);
        } catch (Exception e) {
            log.warn("清除通知缓存失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    /**
     * 批量获取用户信息
     */
    private Map<String, UserSimpleDTO> batchGetUsers(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Set<Long> parsedUserIds = userIds.stream()
                .map(this::parseUserId)
                .flatMap(Optional::stream)
                .collect(Collectors.toSet());
        if (parsedUserIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            ApiResponse<Map<Long, UserSimpleDTO>> response = userServiceClient.batchGetUsersSimple(parsedUserIds);

            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData().values().stream()
                        .collect(Collectors.toMap(
                                user -> String.valueOf(user.getId()),
                                Function.identity(),
                                (existing, replacement) -> existing
                        ));
            }
        } catch (Exception e) {
            log.warn("批量获取用户信息失败: userIds={}, error={}", userIds, e.getMessage());
            throw new BusinessException(ResultCode.SERVICE_DEGRADED, USER_SERVICE_DEGRADED_MESSAGE);
        }

        throw new BusinessException(ResultCode.SERVICE_DEGRADED, USER_SERVICE_DEGRADED_MESSAGE);
    }

    private Optional<Long> parseUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(userId));
        } catch (NumberFormatException e) {
            log.warn("跳过非法用户ID: userId={}", userId);
            return Optional.empty();
        }
    }

    /**
     * 从缓存获取聚合结果
     */
    private PageResult<AggregatedNotificationVO> getCachedResult(Long userId, int page, int size) {
        try {
            return notificationAggregationStore.get(userId, page, size);
        } catch (Exception e) {
            log.warn("获取通知缓存失败: userId={}, page={}, size={}, error={}", userId, page, size, e.getMessage());
        }
        return null;
    }

    /**
     * 缓存聚合结果
     */
    private void cacheResult(Long userId, int page, int size, PageResult<AggregatedNotificationVO> result) {
        try {
            notificationAggregationStore.set(userId, page, size, result, aggregationPolicy.cacheTtl());
        } catch (Exception e) {
            log.warn("缓存通知结果失败: userId={}, page={}, size={}, error={}", userId, page, size, e.getMessage());
        }
    }

    private AggregatedNotificationDTO toAggregatedNotification(NotificationGroupState groupState) {
        return AggregatedNotificationDTO.builder()
                .type(groupState.getNotificationType())
                .targetType(groupState.getTargetType())
                .targetId(groupState.getTargetId())
                .totalCount(groupState.getTotalCount())
                .unreadCount(groupState.getUnreadCount())
                .latestTime(groupState.getLatestTime())
                .latestNotificationId(groupState.getLatestNotificationId() != null
                        ? String.valueOf(groupState.getLatestNotificationId()) : null)
                .latestContent(groupState.getLatestContent())
                .actorIds(groupState.getActorIds())
                .build();
    }

    /**
     * 构建聚合通知VO
     */
    private AggregatedNotificationVO buildAggregatedVO(
            AggregatedNotificationDTO dto, Map<String, UserSimpleDTO> userMap) {

        // 取最近的几个用户（使用配置的最大值）
        List<UserSimpleDTO> recentActors = Collections.emptyList();
        if (dto.getActorIds() != null && !dto.getActorIds().isEmpty()) {
            int maxActors = aggregationPolicy.maxRecentActors();
            recentActors = dto.getActorIds().stream()
                    .limit(maxActors)
                    .map(userMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        // 构建聚合文案
        String aggregatedContent = buildAggregatedContent(
                dto.getType(),
                recentActors,
                dto.getTotalCount()
        );

        return AggregatedNotificationVO.builder()
                .type(dto.getType())
                .targetType(dto.getTargetType())
                .targetId(dto.getTargetId() != null ? Long.valueOf(dto.getTargetId()) : null)
                .totalCount(dto.getTotalCount())
                .unreadCount(dto.getUnreadCount())
                .latestTime(dto.getLatestTime())
                .latestContent(dto.getLatestContent())
                .recentActors(recentActors)
                .aggregatedContent(aggregatedContent)
                .build();
    }

    /**
     * 构建聚合文案
     * 
     * @param type 通知类型
     * @param actors 触发者列表
     * @param totalCount 总数
     * @return 聚合文案
     */
    private String buildAggregatedContent(NotificationType type,
                                          List<UserSimpleDTO> actors, int totalCount) {
        if (actors == null || actors.isEmpty()) {
            return getActionText(type);
        }

        String firstActorName = actors.get(0).getNickname();
        if (firstActorName == null || firstActorName.isEmpty()) {
            firstActorName = actors.get(0).getUserName();
        }

        if (totalCount == 1) {
            return firstActorName + getActionText(type);
        }

        return String.format("%s等%d人%s", firstActorName, totalCount, getActionText(type));
    }

    /**
     * 获取动作文案
     */
    private String getActionText(NotificationType type) {
        if (type == null) {
            return "与你互动";
        }
        return switch (type) {
            case LIKE -> "赞了你的内容";
            case COMMENT -> "评论了你的文章";
            case FOLLOW -> "关注了你";
            case REPLY -> "回复了你的评论";
            case SYSTEM -> "发送了系统通知";
            default -> "发送了通知";
        };
    }

    private record ProjectionResult(List<AggregatedNotificationDTO> aggregatedList, int totalGroups) {
    }
}
