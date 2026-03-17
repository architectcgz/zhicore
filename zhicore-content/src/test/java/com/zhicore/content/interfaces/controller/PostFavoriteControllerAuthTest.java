package com.zhicore.content.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.service.command.PostFavoriteCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("PostFavoriteCommandController 鉴权测试")
class PostFavoriteControllerAuthTest {

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("未登录收藏文章时应该返回未授权")
    void shouldReturnUnauthorizedWhenFavoritePostWithoutLogin() throws Exception {
        PostFavoriteCommandController controller = new PostFavoriteCommandController(mock(PostFavoriteCommandService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        UserContext.clear();

        mockMvc.perform(post("/api/v1/posts/{postId}/favorite", 1001L))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                .andExpect(jsonPath("$.message").value("请先登录"));
    }
}
