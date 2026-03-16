package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.dto.CommentOutboxRetryResponseDTO;
import com.zhicore.comment.application.dto.CommentOutboxSummaryDTO;
import com.zhicore.comment.application.service.command.CommentOutboxAdminService;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.UnauthorizedException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.test.web.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Comment outbox 管理控制器测试")
class CommentOutboxAdminControllerTest extends ControllerTestSupport {

    @Mock
    private CommentOutboxAdminService commentOutboxAdminService;

    @Test
    @DisplayName("未登录查询摘要时应返回未授权")
    void shouldReturnUnauthorizedWhenSummaryWithoutLogin() throws Exception {
        CommentOutboxAdminController controller = new CommentOutboxAdminController(commentOutboxAdminService);
        MockMvc mockMvc = buildMockMvc(controller);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            mockMvc.perform(get("/api/v1/admin/comment-outbox/summary"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("请先登录"));
        }
    }

    @Test
    @DisplayName("已登录查询摘要时应返回统计结果")
    void shouldReturnSummaryWhenLoggedIn() throws Exception {
        CommentOutboxAdminController controller = new CommentOutboxAdminController(commentOutboxAdminService);
        MockMvc mockMvc = buildMockMvc(controller);
        when(commentOutboxAdminService.getSummary()).thenReturn(CommentOutboxSummaryDTO.builder()
                .pendingCount(10)
                .failedCount(1)
                .deadCount(2)
                .succeededCount(24)
                .oldestPendingCreatedAt(LocalDateTime.of(2026, 3, 16, 11, 0))
                .build());

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(get("/api/v1/admin/comment-outbox/summary"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.pendingCount").value(10))
                    .andExpect(jsonPath("$.data.failedCount").value(1))
                    .andExpect(jsonPath("$.data.deadCount").value(2))
                    .andExpect(jsonPath("$.data.succeededCount").value(24));
        }
    }

    @Test
    @DisplayName("批量重试 dead 时应返回重试结果")
    void shouldRetryDeadWhenLoggedIn() throws Exception {
        CommentOutboxAdminController controller = new CommentOutboxAdminController(commentOutboxAdminService);
        MockMvc mockMvc = buildMockMvc(controller);
        when(commentOutboxAdminService.retryDeadEvents(1001L)).thenReturn(CommentOutboxRetryResponseDTO.builder()
                .retriedCount(52)
                .pendingCount(52)
                .deadCount(0)
                .build());

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(post("/api/v1/admin/comment-outbox/retry-dead"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.retriedCount").value(52))
                    .andExpect(jsonPath("$.data.pendingCount").value(52))
                    .andExpect(jsonPath("$.data.deadCount").value(0));
        }
    }
}
