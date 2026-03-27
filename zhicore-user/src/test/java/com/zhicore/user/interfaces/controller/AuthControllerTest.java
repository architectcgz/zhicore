package com.zhicore.user.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.dto.UserVO;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.application.service.command.AuthCommandService;
import com.zhicore.user.application.service.command.UserCommandService;
import com.zhicore.user.interfaces.dto.request.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 测试")
class AuthControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthCommandService authCommandService;

    @Mock
    private UserCommandService userCommandService;

    @Mock
    private UserQueryPort userQueryPort;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authCommandService, userCommandService, userQueryPort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("注册成功时应该返回字符串用户ID")
    void shouldRegisterAndReturnStringUserId() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUserName("test_user");
        request.setEmail("test@example.com");
        request.setPassword("Password123");
        when(userCommandService.register(org.mockito.ArgumentMatchers.any())).thenReturn(1234567890123456789L);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("1234567890123456789"));
    }

    @Test
    @DisplayName("获取当前登录用户时应该返回用户详情")
    void shouldReturnCurrentUserProfile() throws Exception {
        UserVO user = new UserVO();
        user.setId(1234567890123456789L);
        user.setUserName("current-user");
        user.setNickName("当前用户");
        when(userQueryPort.getUserById(1234567890123456789L)).thenReturn(user);

        UserContext.UserInfo userInfo = new UserContext.UserInfo();
        userInfo.setUserId("1234567890123456789");
        userInfo.setUserName("current-user");
        UserContext.setUser(userInfo);

        try {
            mockMvc.perform(get("/api/v1/auth/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("1234567890123456789"))
                    .andExpect(jsonPath("$.data.userName").value("current-user"))
                    .andExpect(jsonPath("$.data.nickName").value("当前用户"));
        } finally {
            UserContext.clear();
        }
    }
}
