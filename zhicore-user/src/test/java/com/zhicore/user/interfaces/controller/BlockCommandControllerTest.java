package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.service.command.BlockCommandService;
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
@DisplayName("BlockCommandController 测试")
class BlockCommandControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BlockCommandService blockCommandService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BlockCommandController(blockCommandService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功拉黑用户")
    void shouldBlock() throws Exception {
        mockMvc.perform(post("/api/v1/users/{blockerId}/blocking/{blockedId}", 1L, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(blockCommandService).block(1L, 2L);
    }
}
