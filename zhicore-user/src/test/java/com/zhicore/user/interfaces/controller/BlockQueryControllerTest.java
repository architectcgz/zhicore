package com.zhicore.user.interfaces.controller;

import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.service.BlockQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("BlockQueryController 测试")
class BlockQueryControllerTest {

    private MockMvc mockMvc;

    @Mock
    private BlockQueryService blockQueryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BlockQueryController(blockQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功检查拉黑关系")
    void shouldCheckBlocked() throws Exception {
        when(blockQueryService.isBlocked(1L, 2L)).thenReturn(true);

        mockMvc.perform(get("/api/v1/users/{blockerId}/blocking/{blockedId}/check", 1L, 2L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }
}
