package com.zhicore.user.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.service.UserCommandService;
import com.zhicore.user.interfaces.dto.request.UpdateStrangerMessageSettingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserCommandController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserCommandController 测试")
class UserCommandControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserCommandService userCommandService;

    @BeforeEach
    void setUp() {
        UserCommandController controller = new UserCommandController(userCommandService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功更新陌生人消息设置")
    void shouldUpdateStrangerMessageSetting() throws Exception {
        UpdateStrangerMessageSettingRequest request = new UpdateStrangerMessageSettingRequest();
        request.setAllowStrangerMessage(true);

        mockMvc.perform(put("/api/v1/users/{userId}/settings/stranger-message", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"));

        verify(userCommandService).updateStrangerMessageSetting(1L, true);
    }

    @Test
    @DisplayName("更新陌生人消息设置缺少字段时应该返回400")
    void shouldRejectWhenAllowStrangerMessageIsMissing() throws Exception {
        mockMvc.perform(put("/api/v1/users/{userId}/settings/stranger-message", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
