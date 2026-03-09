package com.zhicore.user.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.application.service.UserApplicationService;
import com.zhicore.user.interfaces.dto.request.UpdateStrangerMessageSettingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 测试")
class UserControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserQueryPort userQueryPort;

    @Mock
    private UserApplicationService userApplicationService;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(userQueryPort, userApplicationService);
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("应该成功获取陌生人消息设置")
    void shouldGetStrangerMessageSetting() throws Exception {
        when(userQueryPort.isStrangerMessageAllowed(1L)).thenReturn(false);

        mockMvc.perform(get("/api/v1/users/{userId}/settings/stranger-message", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("操作成功"))
                .andExpect(jsonPath("$.data").value(false));
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

        verify(userApplicationService).updateStrangerMessageSetting(1L, true);
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
