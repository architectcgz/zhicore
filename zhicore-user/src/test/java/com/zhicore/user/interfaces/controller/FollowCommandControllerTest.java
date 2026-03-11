package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.service.FollowCommandService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FollowCommandController 测试")
class FollowCommandControllerTest {

    private MockMvc mockMvc;

    @Mock
    private FollowCommandService followCommandService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FollowCommandController(followCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功取消关注")
    void shouldUnfollow() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}/following/{targetUserId}", 1L, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(followCommandService).unfollow(1L, 2L);
    }
}
