package com.zhicore.comment.interfaces.controller;

import com.zhicore.comment.application.command.UpdateCommentCommand;
import com.zhicore.comment.application.dto.CommentSortType;
import com.zhicore.comment.application.dto.CommentVO;
import com.zhicore.comment.application.service.command.CommentCommandService;
import com.zhicore.comment.application.service.query.CommentQueryService;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.comment.interfaces.dto.request.CreateCommentRequest;
import com.zhicore.comment.interfaces.dto.request.UpdateCommentRequest;
import com.zhicore.common.constant.CommonConstants;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.UnauthorizedException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.test.web.ControllerTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Comment query/command controller 测试")
class CommentControllerTest extends ControllerTestSupport {

    @Mock
    private CommentCommandService commentCommandService;

    @Mock
    private CommentQueryService commentQueryService;

    @Test
    @DisplayName("创建评论成功时应该返回字符串评论ID")
    void shouldCreateCommentAndReturnStringId() throws Exception {
        CommentCommandController controller = new CommentCommandController(commentCommandService);
        MockMvc mockMvc = buildMockMvc(controller);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(1234567890123456789L);
        request.setContent("测试评论");
        when(commentCommandService.createComment(
                1001L,
                new com.zhicore.comment.application.command.CreateCommentCommand(
                        1234567890123456789L,
                        "测试评论",
                        null,
                        null,
                        null,
                        null,
                        null
                ))).thenReturn(2234567890123456789L);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(post("/api/v1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").value("2234567890123456789"));
        }
    }

    @Test
    @DisplayName("未登录创建评论时应该返回未授权")
    void shouldReturnUnauthorizedWhenCreateCommentWithoutLogin() throws Exception {
        CommentCommandController controller = new CommentCommandController(commentCommandService);
        MockMvc mockMvc = buildMockMvc(controller);
        CreateCommentRequest request = new CreateCommentRequest();
        request.setPostId(1001L);
        request.setContent("测试评论");

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            mockMvc.perform(post("/api/v1/comments")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("请先登录"));
        }
    }

    @Test
    @DisplayName("更新评论无权操作时应该返回业务错误响应")
    void shouldReturnBusinessErrorWhenUpdateNotAllowed() throws Exception {
        CommentCommandController controller = new CommentCommandController(commentCommandService);
        MockMvc mockMvc = buildMockMvc(controller);
        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("更新后的内容");

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);
            doThrow(new BusinessException(ResultCode.OPERATION_NOT_ALLOWED, "只能编辑自己的评论"))
                    .when(commentCommandService).updateComment(1001L, 101L, new UpdateCommentCommand("更新后的内容"));

            mockMvc.perform(put("/api/v1/comments/{commentId}", 101L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.OPERATION_NOT_ALLOWED.getCode()))
                    .andExpect(jsonPath("$.message").value("只能编辑自己的评论"));
        }
    }

    @Test
    @DisplayName("未登录更新评论时应该返回未授权")
    void shouldReturnUnauthorizedWhenUpdateCommentWithoutLogin() throws Exception {
        CommentCommandController controller = new CommentCommandController(commentCommandService);
        MockMvc mockMvc = buildMockMvc(controller);
        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("更新后的内容");

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            mockMvc.perform(put("/api/v1/comments/{commentId}", 101L)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("请先登录"));
        }
    }

    @Test
    @DisplayName("删除已删除评论时应该返回业务错误响应")
    void shouldReturnBusinessErrorWhenDeleteDeletedComment() throws Exception {
        CommentCommandController controller = new CommentCommandController(commentCommandService);
        MockMvc mockMvc = buildMockMvc(controller);
        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);
            userContext.when(UserContext::isAdmin).thenReturn(false);
            doThrow(new BusinessException(ResultCode.COMMENT_ALREADY_DELETED, "评论已经删除"))
                    .when(commentCommandService).deleteComment(1001L, false, 101L);

            mockMvc.perform(delete("/api/v1/comments/{commentId}", 101L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.COMMENT_ALREADY_DELETED.getCode()))
                    .andExpect(jsonPath("$.message").value("评论已经删除"));
        }
    }

    @Test
    @DisplayName("获取评论详情时应该返回字符串ID")
    void shouldGetCommentWithStringIds() throws Exception {
        CommentQueryController controller = new CommentQueryController(commentQueryService);
        MockMvc mockMvc = buildMockMvc(controller);
        UserSimpleDTO author = new UserSimpleDTO();
        author.setId(3234567890123456789L);
        author.setUserName("author");
        author.setNickname("作者");

        CommentVO comment = CommentVO.builder()
                .id(2234567890123456789L)
                .postId(1234567890123456789L)
                .rootId(1134567890123456789L)
                .content("评论内容")
                .author(author)
                .build();
        when(commentQueryService.getComment(2234567890123456789L)).thenReturn(comment);

        mockMvc.perform(get("/api/v1/comments/{commentId}", 2234567890123456789L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("2234567890123456789"))
                .andExpect(jsonPath("$.data.postId").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.rootId").value("1134567890123456789"))
                .andExpect(jsonPath("$.data.author.id").value("3234567890123456789"));
    }

    @Test
    @DisplayName("无效游标时应该返回参数错误响应")
    void shouldReturnParamErrorWhenCursorIsInvalid() throws Exception {
        CommentQueryController controller = new CommentQueryController(commentQueryService);
        MockMvc mockMvc = buildMockMvc(controller);
        doThrow(new BusinessException(ResultCode.PARAM_ERROR, "无效的分页游标"))
                .when(commentQueryService).getTopLevelCommentsByCursor(1001L, "bad-cursor", 20, CommentSortType.TIME);

        mockMvc.perform(get("/api/v1/comments/post/{postId}/cursor", 1001L)
                        .param("cursor", "bad-cursor")
                        .param("size", "20")
                        .param("sort", "TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("无效的分页游标"));
    }

    @Test
    @DisplayName("分页大小非法时应该命中方法参数校验")
    void shouldRejectInvalidPageSize() throws Exception {
        CommentQueryController controller = new CommentQueryController(commentQueryService);
        var method = CommentQueryController.class.getMethod(
                "getCommentsByPage", Long.class, int.class, int.class, CommentSortType.class);
        var violations = executableValidator.validateParameters(
                controller, method, new Object[]{1001L, 0, 0, CommentSortType.TIME});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("每页大小必须为正数", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("负页码时应该命中方法参数校验")
    void shouldRejectNegativePage() throws Exception {
        CommentQueryController controller = new CommentQueryController(commentQueryService);
        var method = CommentQueryController.class.getMethod(
                "getRepliesByPage", Long.class, int.class, int.class);
        var violations = executableValidator.validateParameters(
                controller, method, new Object[]{1001L, -1, 20});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("页码不能为负数", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("超大分页大小时应该命中方法参数校验")
    void shouldRejectOversizedPageSize() throws Exception {
        CommentQueryController controller = new CommentQueryController(commentQueryService);
        var method = CommentQueryController.class.getMethod(
                "getCommentsByCursor", Long.class, String.class, int.class, CommentSortType.class);
        var violations = executableValidator.validateParameters(
                controller, method, new Object[]{1001L, null, 101, CommentSortType.TIME});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("每页大小不能大于100", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("非法排序参数时应该返回参数错误")
    void shouldReturnParamErrorWhenSortIsInvalid() throws Exception {
        CommentQueryController controller = new CommentQueryController(commentQueryService);
        MockMvc mockMvc = buildMockMvc(controller);

        mockMvc.perform(get("/api/v1/comments/post/{postId}/page", 1001L)
                        .param("page", "0")
                        .param("size", "20")
                        .param("sort", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("参数类型错误: sort"));
    }

    @Test
    @DisplayName("传统分页超出查询窗口时应该返回参数错误")
    void shouldReturnParamErrorWhenTraditionalPaginationWindowExceeded() throws Exception {
        CommentQueryController controller = new CommentQueryController(commentQueryService);
        MockMvc mockMvc = buildMockMvc(controller);
        doThrow(new BusinessException(
                ResultCode.PARAM_ERROR,
                "传统分页仅支持前" + CommonConstants.MAX_OFFSET_WINDOW + "条数据，请改用游标分页"))
                .when(commentQueryService).getTopLevelCommentsByPage(1001L, 50, 100, CommentSortType.TIME);

        mockMvc.perform(get("/api/v1/comments/post/{postId}/page", 1001L)
                        .param("page", "50")
                        .param("size", "100")
                        .param("sort", "TIME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value(
                        "传统分页仅支持前" + CommonConstants.MAX_OFFSET_WINDOW + "条数据，请改用游标分页"));
    }
}
