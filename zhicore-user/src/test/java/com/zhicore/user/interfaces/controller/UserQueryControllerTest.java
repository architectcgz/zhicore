package com.zhicore.user.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.user.application.port.UserQueryPort;
import com.zhicore.user.application.query.view.UserSimpleView;
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

/**
 * UserQueryController 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserQueryController 测试")
class UserQueryControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserQueryPort userQueryPort;

    @BeforeEach
    void setUp() {
        UserQueryController controller = new UserQueryController(userQueryPort);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
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
    @DisplayName("应该成功获取用户简要信息")
    void shouldGetUserSimple() throws Exception {
        UserSimpleView view = new UserSimpleView();
        view.setId(1L);
        view.setUserName("testuser");
        view.setNickname("测试用户");
        view.setAvatarId("avatar-1");
        view.setProfileVersion(3L);
        when(userQueryPort.getUserSimpleById(1L)).thenReturn(view);

        mockMvc.perform(get("/api/v1/users/{userId}/simple", 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.userName").value("testuser"))
                .andExpect(jsonPath("$.data.nickname").value("测试用户"))
                .andExpect(jsonPath("$.data.avatarId").value("avatar-1"))
                .andExpect(jsonPath("$.data.profileVersion").value(3));
    }

    @Test
    @DisplayName("应该成功批量获取用户简要信息")
    void shouldBatchGetUsersSimple() throws Exception {
        UserSimpleView view = new UserSimpleView();
        view.setId(1L);
        view.setUserName("testuser");
        view.setNickname("测试用户");
        when(userQueryPort.batchGetUsersSimple(java.util.Set.of(1L))).thenReturn(java.util.Map.of(1L, view));

        mockMvc.perform(post("/api/v1/users/batch/simple")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Set.of(1L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.1.id").value(1))
                .andExpect(jsonPath("$.data.1.userName").value("testuser"))
                .andExpect(jsonPath("$.data.1.nickname").value("测试用户"));
    }
}
