package com.zhicore.notification.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDigestJob {

    private Long recipientId;
    private int itemCount;
    private Instant windowStart;
    private Instant windowEnd;
    private String groupKey;
    private String content;

    public static NotificationDigestJob fromDeliveries(Long recipientId, List<NotificationDelivery> deliveries) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        return NotificationDigestJob.builder()
                .recipientId(recipientId)
                .itemCount(deliveries.size())
                .windowStart(deliveries.stream()
                        .map(NotificationDelivery::getCreatedAt)
                        .filter(java.util.Objects::nonNull)
                        .min(Instant::compareTo)
                        .orElse(now))
                .windowEnd(deliveries.stream()
                        .map(NotificationDelivery::getUpdatedAt)
                        .filter(java.util.Objects::nonNull)
                        .max(Instant::compareTo)
                        .orElse(now))
                .groupKey("author_publish_digest:" + recipientId + ":" + today)
                .content("你关注的作者有 " + deliveries.size() + " 篇新作品更新")
                .build();
    }
}
