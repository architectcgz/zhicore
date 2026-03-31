package com.zhicore.notification.application.service.channel;

import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationInboxDeliveryService {

    private final NotificationCommandService notificationCommandService;

    public Notification deliverPostPublished(Long recipientId,
                                             Long authorId,
                                             Long postId,
                                             String groupKey,
                                             String content) {
        return notificationCommandService.createPostPublishedNotification(
                recipientId,
                authorId,
                postId,
                groupKey,
                content
        );
    }

    public Notification deliverDigestSummary(Long recipientId,
                                             String groupKey,
                                             String content) {
        return notificationCommandService.createPostPublishedDigestNotification(
                recipientId,
                groupKey,
                content
        );
    }
}
