package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.result.PageResult;
import com.zhicore.user.application.query.view.UserManageView;
import com.zhicore.user.application.service.UserManageQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminUserQueryController 测试")
class AdminUserQueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserManageQueryService userManageQueryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AdminUserQueryController(userManageQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功查询管理员用户列表")
    void shouldQueryUsers() throws Exception {
        UserManageView view = UserManageView.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .nickname("测试用户")
                .avatar("avatar-1")
                .status("ACTIVE")
                .createdAt(LocalDateTime.of(2026, 3, 11, 10, 0))
                .roles(List.of("USER"))
                .build();
        when(userManageQueryService.queryUsers("test", "ACTIVE", 1, 20))
                .thenReturn(PageResult.of(1, 20, 1, List.of(view)));

        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", "test")
                        .param("status", "ACTIVE")
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("testuser"))
                .andExpect(jsonPath("$.data.records[0].status").value("ACTIVE"));
    }
}
