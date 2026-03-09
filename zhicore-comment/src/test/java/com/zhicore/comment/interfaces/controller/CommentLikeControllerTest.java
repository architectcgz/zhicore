package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.service.CommentLikeApplicationService;
import com.zhicore.comment.interfaces.dto.request.BatchCheckLikedRequest;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.test.web.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommentLikeController 测试")
class CommentLikeControllerTest extends ControllerTestSupport {

    @Mock
    private CommentLikeApplicationService likeService;

    @Test
    @DisplayName("评论已删除时应该返回业务错误响应")
    void shouldReturnBusinessErrorWhenCommentDeleted() throws Exception {
        CommentLikeController controller = new CommentLikeController(likeService);
        MockMvc mockMvc = buildMockMvc(controller);
        doThrow(new BusinessException(ResultCode.COMMENT_ALREADY_DELETED, "评论已删除，无法点赞"))
                .when(likeService).likeComment(1001L, 2001L);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(post("/api/v1/comments/{commentId}/like", 2001L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.COMMENT_ALREADY_DELETED.getCode()))
                    .andExpect(jsonPath("$.message").value("评论已删除，无法点赞"));
        }
    }

    @Test
    @DisplayName("批量点赞状态请求为空列表时应该返回参数错误")
    void shouldRejectEmptyBatchCheckLikedRequest() throws Exception {
        CommentLikeController controller = new CommentLikeController(likeService);
        MockMvc mockMvc = buildMockMvc(controller);
        BatchCheckLikedRequest request = new BatchCheckLikedRequest();
        request.setCommentIds(List.of());

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(post("/api/v1/comments/batch/liked")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()))
                    .andExpect(jsonPath("$.message").value("评论ID列表不能为空"));
        }
    }

    @Test
    @DisplayName("批量点赞状态请求包含非法评论ID时应该返回参数错误")
    void shouldRejectBatchCheckLikedRequestWithInvalidCommentId() throws Exception {
        CommentLikeController controller = new CommentLikeController(likeService);
        MockMvc mockMvc = buildMockMvc(controller);
        BatchCheckLikedRequest request = new BatchCheckLikedRequest();
        request.setCommentIds(List.of(1L, 0L));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(post("/api/v1/comments/batch/liked")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()))
                    .andExpect(jsonPath("$.message").value("评论ID必须为正数"));
        }
    }

    @Test
    @DisplayName("批量点赞状态请求合法时应该返回结果")
    void shouldBatchCheckLikedSuccessfully() throws Exception {
        CommentLikeController controller = new CommentLikeController(likeService);
        MockMvc mockMvc = buildMockMvc(controller);
        BatchCheckLikedRequest request = new BatchCheckLikedRequest();
        request.setCommentIds(List.of(1L, 2L));
        when(likeService.batchCheckLiked(1001L, List.of(1L, 2L)))
                .thenReturn(java.util.Map.of(1L, true, 2L, false));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(post("/api/v1/comments/batch/liked")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data['1']").value(true))
                    .andExpect(jsonPath("$.data['2']").value(false));
        }
    }
}
