package com.zhicore.notification.domain.repository;

import com.zhicore.notification.application.dto.AggregatedNotificationDTO;
import com.zhicore.notification.domain.model.Notification;
import com.zhicore.notification.domain.model.NotificationType;

import java.util.List;
import java.util.Optional;

/**
 * 通知仓储接口
 *
 * @author ZhiCore Team
 */
public interface NotificationRepository {

    /**
     * 保存通知
     *
     * @param notification 通知
     */
    void save(Notification notification);

    /**
     * 仅在通知不存在时保存。
     *
     * @param notification 通知
     * @return true 表示首次写入成功，false 表示已存在
     */
    boolean saveIfAbsent(Notification notification);

    /**
     * 根据ID查询通知
     *
     * @param id 通知ID
     * @return 通知
     */
    Optional<Notification> findById(Long id);

    /**
     * 查询用户的通知列表
     *
     * @param recipientId 接收者ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 通知列表
     */
    List<Notification> findByRecipientId(String recipientId, int page, int size);

    /**
     * 聚合查询通知
     * 先按 (type, target_type, target_id) 分组，再分页
     *
     * @param recipientId 接收者ID
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @return 聚合后的通知列表
     */
    List<AggregatedNotificationDTO> findAggregatedNotifications(Long recipientId, int page, int size);

    /**
     * 获取聚合组总数（用于分页）
     *
     * @param recipientId 接收者ID
     * @return 聚合组总数
     */
    int countAggregatedGroups(Long recipientId);

    /**
     * 查询单个聚合组的聚合结果。
     *
     * @param recipientId 接收者ID
     * @param type 通知类型
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @return 聚合结果
     */
    Optional<AggregatedNotificationDTO> findAggregatedNotificationByGroup(Long recipientId,
                                                                          NotificationType type,
                                                                          String targetType,
                                                                          Long targetId);

    /**
     * 查询某个聚合组的详细通知列表
     *
     * @param recipientId 接收者ID
     * @param type 通知类型
     * @param targetType 目标类型
     * @param targetId 目标ID
     * @param limit 限制数量
     * @return 通知列表
     */
    List<Notification> findByGroup(Long recipientId, NotificationType type,
                                   String targetType, Long targetId, int limit);

    /**
     * 统计未读通知数量
     *
     * @param recipientId 接收者ID
     * @return 未读数量
     */
    int countUnread(Long recipientId);

    /**
     * 标记单条通知为已读
     *
     * @param id 通知ID
     * @param recipientId 接收者ID
     */
    void markAsRead(Long id, Long recipientId);

    /**
     * 批量标记所有通知为已读
     *
     * @param recipientId 接收者ID
     */
    void markAllAsRead(Long recipientId);

    /**
     * 删除通知
     *
     * @param id 通知ID
     */
    void delete(Long id);
}
