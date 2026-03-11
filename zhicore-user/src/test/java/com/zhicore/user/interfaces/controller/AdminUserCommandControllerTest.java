package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.service.AdminUserCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserCommandController 测试")
class AdminUserCommandControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AdminUserCommandService adminUserCommandService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserCommandController(adminUserCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功禁用用户")
    void shouldDisableUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/disable", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminUserCommandService).disableUser(1L);
    }

    @Test
    @DisplayName("应该成功启用用户")
    void shouldEnableUser() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/enable", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminUserCommandService).enableUser(1L);
    }

    @Test
    @DisplayName("应该成功使用户 token 失效")
    void shouldInvalidateUserTokens() throws Exception {
        mockMvc.perform(post("/api/v1/admin/users/{userId}/invalidate-tokens", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(adminUserCommandService).invalidateUserTokens(1L);
    }
}
