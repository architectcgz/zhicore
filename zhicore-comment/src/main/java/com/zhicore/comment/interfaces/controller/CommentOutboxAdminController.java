package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.dto.CommentOutboxRetryResponseDTO;
import com.zhicore.comment.application.dto.CommentOutboxSummaryDTO;
import com.zhicore.comment.application.service.command.CommentOutboxAdminService;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评论 outbox 管理接口。
 */
@Tag(name = "管理员-评论Outbox", description = "评论服务 outbox 摘要与重试接口")
@RestController
@RequestMapping("/api/v1/admin/comment-outbox")
@RequiredArgsConstructor
public class CommentOutboxAdminController {

    private final CommentOutboxAdminService commentOutboxAdminService;

    @Operation(summary = "查询评论 outbox 摘要", description = "查看评论 outbox 的 pending/failed/dead/succeeded 状态统计")
    @GetMapping("/summary")
    public ApiResponse<CommentOutboxSummaryDTO> summary() {
        UserContext.requireUserId();
        return ApiResponse.success(commentOutboxAdminService.getSummary());
    }

    @Operation(summary = "批量重试 DEAD 评论 outbox", description = "将评论 outbox 中所有 DEAD 事件重置为 PENDING 并重新派发")
    @PostMapping("/retry-dead")
    public ApiResponse<CommentOutboxRetryResponseDTO> retryDead() {
        Long operatorId = UserContext.requireUserId();
        return ApiResponse.success(commentOutboxAdminService.retryDeadEvents(operatorId));
    }
}
