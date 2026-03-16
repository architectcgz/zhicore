package com.zhicore.comment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 评论 outbox 摘要视图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentOutboxSummaryDTO {

    private long pendingCount;
    private long failedCount;
    private long deadCount;
    private long succeededCount;
    private LocalDateTime oldestPendingCreatedAt;
}
