package com.zhicore.user.integration;

import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.user.application.command.LoginCommand;
import com.zhicore.user.application.command.RegisterCommand;
import com.zhicore.user.application.dto.TokenVO;
import com.zhicore.user.application.service.AuthApplicationService;
import com.zhicore.user.application.service.UserApplicationService;
import com.zhicore.user.interfaces.dto.request.LoginRequest;
import com.zhicore.user.interfaces.dto.request.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * User Service API 集成测试
 * 
 * 测试用户注册、登录、关注等核心功能
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("User Service API 集成测试")
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserApplicationService userApplicationService;

    @MockBean
    private AuthApplicationService authApplicationService;

    @MockBean
    private RocketMQTemplate rocketMQTemplate;

    @Test
    @Order(1)
    @DisplayName("用户注册应成功")
    void register_shouldSucceed() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUserName("testuser");
        request.setEmail("test@example.com");
        request.setPassword("Test123456!");

        when(userApplicationService.register(any(RegisterCommand.class))).thenReturn(1001L);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data").value(1001))
            .andReturn();

        String response = result.getResponse().getContentAsString();
        // 保存用户ID供后续测试使用
    }

    @Test
    @Order(2)
    @DisplayName("重复邮箱注册应失败")
    void register_withDuplicateEmail_shouldFail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUserName("testuser2");
        request.setEmail("test@example.com");
        request.setPassword("Test123456!");

        doThrow(new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS))
                .when(userApplicationService).register(any(RegisterCommand.class));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ResultCode.EMAIL_ALREADY_EXISTS.getCode()));
    }

    @Test
    @Order(3)
    @DisplayName("用户登录应返回 Token")
    void login_shouldReturnToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("Test123456!");

        when(authApplicationService.login(any(LoginCommand.class)))
                .thenReturn(new TokenVO("access-token", "refresh-token", 3600L));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").exists())
            .andExpect(jsonPath("$.data.refreshToken").exists())
            .andReturn();

        // 保存 Token 供后续测试使用
        String response = result.getResponse().getContentAsString();
        // accessToken = extractAccessToken(response);
    }

    @Test
    @Order(4)
    @DisplayName("错误密码登录应失败")
    void login_withWrongPassword_shouldFail() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("WrongPassword!");

        doThrow(new BusinessException(ResultCode.LOGIN_FAILED, "邮箱或密码错误"))
                .when(authApplicationService).login(any(LoginCommand.class));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value(ResultCode.LOGIN_FAILED.getCode()));
    }

    @Test
    @Order(5)
    @DisplayName("健康检查端点应返回 UP")
    void healthEndpoint_shouldReturnUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(6)
    @DisplayName("Prometheus 指标端点应可访问")
    void prometheusEndpoint_shouldBeAccessible() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory")));
    }
}
