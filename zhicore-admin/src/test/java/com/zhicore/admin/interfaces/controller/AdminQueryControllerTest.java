package com.zhicore.admin.interfaces.controller;

import com.zhicore.admin.application.dto.CommentManageVO;
import com.zhicore.admin.application.dto.PostManageVO;
import com.zhicore.admin.application.dto.ReportVO;
import com.zhicore.admin.application.dto.UserManageVO;
import com.zhicore.admin.application.service.query.CommentManageQueryService;
import com.zhicore.admin.application.service.query.PostManageQueryService;
import com.zhicore.admin.application.service.query.ReportManageQueryService;
import com.zhicore.admin.application.service.query.UserManageQueryService;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.result.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Admin 查询控制器测试")
class AdminQueryControllerTest {

    @Mock
    private UserManageQueryService userManageQueryService;

    @Mock
    private PostManageQueryService postManageQueryService;

    @Mock
    private CommentManageQueryService commentManageQueryService;

    @Mock
    private ReportManageQueryService reportManageQueryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new UserManageQueryController(userManageQueryService),
                        new PostManageQueryController(postManageQueryService),
                        new CommentManageQueryController(commentManageQueryService),
                        new ReportManageQueryController(reportManageQueryService)
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("用户管理列表应该返回字符串ID")
    void shouldReturnStringUserId() throws Exception {
        UserManageVO user = UserManageVO.builder()
                .id(1234567890123456789L)
                .username("admin_user")
                .build();
        when(userManageQueryService.listUsers(null, null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 1, List.of(user)));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("1234567890123456789"))
                .andExpect(jsonPath("$.data.records[0].username").value("admin_user"));
    }

    @Test
    @DisplayName("文章管理列表应该返回字符串ID")
    void shouldReturnStringPostIds() throws Exception {
        PostManageVO post = PostManageVO.builder()
                .id(2234567890123456789L)
                .authorId(3234567890123456789L)
                .title("post")
                .build();
        when(postManageQueryService.listPosts(null, null, null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 1, List.of(post)));

        mockMvc.perform(get("/admin/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("2234567890123456789"))
                .andExpect(jsonPath("$.data.records[0].authorId").value("3234567890123456789"));
    }

    @Test
    @DisplayName("评论管理列表应该返回字符串ID")
    void shouldReturnStringCommentIds() throws Exception {
        CommentManageVO comment = CommentManageVO.builder()
                .id(4234567890123456789L)
                .postId(5234567890123456789L)
                .userId(6234567890123456789L)
                .content("comment")
                .build();
        when(commentManageQueryService.listComments(null, null, null, 1, 20))
                .thenReturn(PageResult.of(1, 20, 1, List.of(comment)));

        mockMvc.perform(get("/admin/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("4234567890123456789"))
                .andExpect(jsonPath("$.data.records[0].postId").value("5234567890123456789"))
                .andExpect(jsonPath("$.data.records[0].userId").value("6234567890123456789"));
    }

    @Test
    @DisplayName("举报管理列表应该返回字符串ID")
    void shouldReturnStringReportIds() throws Exception {
        ReportVO report = ReportVO.builder()
                .id(7234567890123456789L)
                .reporterId(823456789012345678L)
                .reportedUserId(923456789012345678L)
                .targetId(1023456789012345678L)
                .handlerId(1123456789012345678L)
                .status("PENDING")
                .build();
        when(reportManageQueryService.listPendingReports(1, 20))
                .thenReturn(PageResult.of(1, 20, 1, List.of(report)));

        mockMvc.perform(get("/admin/reports/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].id").value("7234567890123456789"))
                .andExpect(jsonPath("$.data.records[0].reporterId").value("823456789012345678"))
                .andExpect(jsonPath("$.data.records[0].reportedUserId").value("923456789012345678"))
                .andExpect(jsonPath("$.data.records[0].targetId").value("1023456789012345678"))
                .andExpect(jsonPath("$.data.records[0].handlerId").value("1123456789012345678"));
    }
}
