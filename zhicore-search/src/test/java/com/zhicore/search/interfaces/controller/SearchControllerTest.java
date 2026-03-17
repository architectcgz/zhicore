package com.zhicore.search.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.exception.UnauthorizedException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.search.application.service.query.SearchQueryService;
import com.zhicore.search.application.service.command.SuggestionCommandService;
import com.zhicore.search.application.service.query.SuggestionQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("SearchController 测试")
class SearchControllerTest {

    @Test
    @DisplayName("未登录获取搜索历史时应该返回未授权")
    void shouldReturnUnauthorizedWhenGetHistoryWithoutLogin() throws Exception {
        SearchQueryController controller = new SearchQueryController(
                mock(SearchQueryService.class),
                mock(SuggestionQueryService.class),
                mock(SuggestionCommandService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            mockMvc.perform(get("/api/v1/search/history"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("请先登录"));
        }
    }

    @Test
    @DisplayName("未登录清空搜索历史时应该返回未授权")
    void shouldReturnUnauthorizedWhenClearHistoryWithoutLogin() throws Exception {
        SearchCommandController controller = new SearchCommandController(
                mock(SuggestionCommandService.class));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            mockMvc.perform(delete("/api/v1/search/history"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("请先登录"));
        }
    }
}
