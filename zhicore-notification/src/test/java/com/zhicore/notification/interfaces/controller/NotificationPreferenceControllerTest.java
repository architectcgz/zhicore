package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.UnauthorizedException;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.test.web.ControllerTestSupport;
import com.zhicore.notification.application.dto.NotificationUserDndDTO;
import com.zhicore.notification.application.dto.NotificationUserPreferenceDTO;
import com.zhicore.notification.application.service.preference.NotificationPreferenceService;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationDndRequest;
import com.zhicore.notification.interfaces.dto.request.UpdateNotificationPreferenceRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification preference/dnd controller 测试")
class NotificationPreferenceControllerTest extends ControllerTestSupport {

    @Mock
    private NotificationPreferenceService notificationPreferenceService;

    @Test
    @DisplayName("查询通知偏好成功时应该返回当前用户配置")
    void shouldGetPreference() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(notificationPreferenceService);
        MockMvc mockMvc = buildMockMvc(controller);
        when(notificationPreferenceService.getPreference(11L)).thenReturn(
                NotificationUserPreferenceDTO.builder()
                        .likeEnabled(true)
                        .commentEnabled(true)
                        .followEnabled(true)
                        .replyEnabled(true)
                        .systemEnabled(true)
                        .publishEnabled(true)
                        .build()
        );

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notifications/preferences"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.likeEnabled").value(true));
        }
    }

    @Test
    @DisplayName("更新通知偏好成功时应该返回更新后配置")
    void shouldUpdatePreference() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(notificationPreferenceService);
        MockMvc mockMvc = buildMockMvc(controller);
        UpdateNotificationPreferenceRequest request = new UpdateNotificationPreferenceRequest();
        request.setLikeEnabled(false);
        request.setCommentEnabled(true);
        request.setFollowEnabled(true);
        request.setReplyEnabled(true);
        request.setSystemEnabled(true);
        request.setPublishEnabled(false);

        when(notificationPreferenceService.updatePreference(eq(11L), any(UpdateNotificationPreferenceRequest.class)))
                .thenReturn(NotificationUserPreferenceDTO.builder()
                        .likeEnabled(false)
                        .commentEnabled(true)
                        .followEnabled(true)
                        .replyEnabled(true)
                        .systemEnabled(true)
                        .publishEnabled(false)
                        .build());

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(put("/api/v1/notifications/preferences")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.likeEnabled").value(false))
                    .andExpect(jsonPath("$.data.publishEnabled").value(false));
        }
    }

    @Test
    @DisplayName("查询免打扰配置成功时应该返回当前用户配置")
    void shouldGetDnd() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(notificationPreferenceService);
        MockMvc mockMvc = buildMockMvc(controller);
        when(notificationPreferenceService.getDnd(11L)).thenReturn(
                NotificationUserDndDTO.builder()
                        .enabled(true)
                        .startTime("22:00")
                        .endTime("08:00")
                        .timezone("Asia/Shanghai")
                        .build()
        );

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notifications/dnd"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.enabled").value(true))
                    .andExpect(jsonPath("$.data.startTime").value("22:00"));
        }
    }

    @Test
    @DisplayName("更新免打扰配置成功时应该返回更新后配置")
    void shouldUpdateDnd() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(notificationPreferenceService);
        MockMvc mockMvc = buildMockMvc(controller);
        UpdateNotificationDndRequest request = new UpdateNotificationDndRequest();
        request.setEnabled(true);
        request.setStartTime("23:00");
        request.setEndTime("07:00");
        request.setTimezone("Asia/Shanghai");

        when(notificationPreferenceService.updateDnd(eq(11L), any(UpdateNotificationDndRequest.class)))
                .thenReturn(NotificationUserDndDTO.builder()
                        .enabled(true)
                        .startTime("23:00")
                        .endTime("07:00")
                        .timezone("Asia/Shanghai")
                        .build());

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(put("/api/v1/notifications/dnd")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.enabled").value(true));
        }
    }

    @Test
    @DisplayName("未登录访问偏好接口时应该返回未授权")
    void shouldReturnUnauthorizedWhenGetPreferenceWithoutLogin() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(notificationPreferenceService);
        MockMvc mockMvc = buildMockMvc(controller);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            mockMvc.perform(get("/api/v1/notifications/preferences"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("请先登录"));
        }
    }

    @Test
    @DisplayName("免打扰时间参数非法时应该命中方法参数校验")
    void shouldRejectInvalidDndTimeWhenEnabled() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(notificationPreferenceService);
        UpdateNotificationDndRequest request = new UpdateNotificationDndRequest();
        request.setEnabled(true);
        request.setStartTime("25:00");
        request.setEndTime("07:00");
        request.setTimezone("Asia/Shanghai");

        var method = NotificationPreferenceController.class.getMethod("updateDnd", UpdateNotificationDndRequest.class);
        var violations = executableValidator.validateParameters(controller, method, new Object[]{request});

        assertEquals(1, violations.size());
    }
}
