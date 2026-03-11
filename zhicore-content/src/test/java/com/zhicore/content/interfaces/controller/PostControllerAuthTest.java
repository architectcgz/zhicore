package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.service.PostCommandService;
import com.zhicore.content.application.service.PostReadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PostController 鉴权测试")
class PostControllerAuthTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未登录创建文章时应该返回未授权")
    void shouldReturnUnauthorizedWhenCreatePostWithoutLogin() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new PostCommandController(mock(PostCommandService.class)),
                        new PostQueryController(mock(PostReadService.class))
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserContext.clear();

        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"test title","content":"test content"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }

    @Test
    @DisplayName("未登录查询我的文章列表时应该返回未授权")
    void shouldReturnUnauthorizedWhenGetMyPostsWithoutLogin() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                        new PostCommandController(mock(PostCommandService.class)),
                        new PostQueryController(mock(PostReadService.class))
                )
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserContext.clear();

        mockMvc.perform(get("/api/v1/posts/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }
}
