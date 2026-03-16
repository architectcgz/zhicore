package com.zhicore.comment.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 评论 outbox dead 重试结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentOutboxRetryResponseDTO {

    private int retriedCount;
    private long pendingCount;
    private long deadCount;
}
