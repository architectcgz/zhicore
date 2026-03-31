package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.test.web.ControllerTestSupport;
import com.zhicore.notification.application.service.command.NotificationPreferenceCommandService;
import com.zhicore.notification.application.service.query.NotificationPreferenceQueryService;
import com.zhicore.notification.domain.model.AuthorSubscriptionLevel;
import com.zhicore.notification.domain.model.NotificationCategory;
import com.zhicore.notification.domain.model.NotificationChannel;
import com.zhicore.notification.domain.model.NotificationType;
import com.zhicore.notification.domain.model.UserAuthorSubscription;
import com.zhicore.notification.domain.model.UserNotificationDnd;
import com.zhicore.notification.domain.model.UserNotificationPreference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification preference controller 测试")
class NotificationPreferenceControllerTest extends ControllerTestSupport {

    @Mock
    private NotificationPreferenceCommandService notificationPreferenceCommandService;

    @Mock
    private NotificationPreferenceQueryService notificationPreferenceQueryService;

    @Test
    @DisplayName("获取通知偏好列表成功时应该返回当前用户偏好")
    void shouldReturnPreferences() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(
                notificationPreferenceCommandService,
                notificationPreferenceQueryService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        when(notificationPreferenceQueryService.getPreferences(11L)).thenReturn(List.of(
                UserNotificationPreference.of(
                        11L,
                        NotificationType.POST_COMMENTED,
                        NotificationChannel.EMAIL,
                        false
                )
        ));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notification-preferences"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data[0].notificationType").value("POST_COMMENTED"))
                    .andExpect(jsonPath("$.data[0].channel").value("EMAIL"))
                    .andExpect(jsonPath("$.data[0].enabled").value(false));
        }
    }

    @Test
    @DisplayName("更新通知偏好成功时应该调用命令服务")
    void shouldUpdatePreference() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(
                notificationPreferenceCommandService,
                notificationPreferenceQueryService
        );
        MockMvc mockMvc = buildMockMvc(controller);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(put("/api/v1/notification-preferences")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "notificationType": "POST_COMMENTED",
                                      "channel": "EMAIL",
                                      "enabled": false
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));
        }

        verify(notificationPreferenceCommandService)
                .updateNotificationPreference(11L, NotificationType.POST_COMMENTED, NotificationChannel.EMAIL, false);
    }

    @Test
    @DisplayName("非法通知渠道时应该返回参数错误")
    void shouldRejectInvalidChannelWhenUpdatingPreference() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(
                notificationPreferenceCommandService,
                notificationPreferenceQueryService
        );
        MockMvc mockMvc = buildMockMvc(controller);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(put("/api/v1/notification-preferences")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "notificationType": "POST_COMMENTED",
                                      "channel": "PIGEON",
                                      "enabled": true
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
        }
    }

    @Test
    @DisplayName("获取 DND 配置成功时应该返回当前配置")
    void shouldReturnDnd() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(
                notificationPreferenceCommandService,
                notificationPreferenceQueryService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        when(notificationPreferenceQueryService.getDnd(11L)).thenReturn(Optional.of(
                UserNotificationDnd.of(
                        11L,
                        true,
                        LocalTime.of(22, 0),
                        LocalTime.of(8, 0),
                        EnumSet.of(NotificationCategory.CONTENT),
                        EnumSet.of(NotificationChannel.WEBSOCKET)
                )
        ));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notification-dnd"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.enabled").value(true))
                    .andExpect(jsonPath("$.data.categories[0]").value("CONTENT"))
                    .andExpect(jsonPath("$.data.channels[0]").value("WEBSOCKET"));
        }
    }

    @Test
    @DisplayName("相同起止时间的 DND 配置应该被拒绝")
    void shouldRejectMalformedDndWindow() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(
                notificationPreferenceCommandService,
                notificationPreferenceQueryService
        );
        MockMvc mockMvc = buildMockMvc(controller);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(put("/api/v1/notification-dnd")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "enabled": true,
                                      "startTime": "22:00",
                                      "endTime": "22:00",
                                      "categories": ["CONTENT"],
                                      "channels": ["WEBSOCKET"]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(ResultCode.PARAM_ERROR.getCode()));
        }
    }

    @Test
    @DisplayName("获取作者订阅成功时应该返回订阅配置")
    void shouldReturnAuthorSubscription() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(
                notificationPreferenceCommandService,
                notificationPreferenceQueryService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        when(notificationPreferenceQueryService.getAuthorSubscription(11L, 22L)).thenReturn(
                UserAuthorSubscription.of(
                        11L,
                        22L,
                        AuthorSubscriptionLevel.DIGEST_ONLY,
                        true,
                        false,
                        true,
                        true
                )
        );

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notification-authors/{authorId}/subscription", 22L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.authorId").value("22"))
                    .andExpect(jsonPath("$.data.level").value("DIGEST_ONLY"))
                    .andExpect(jsonPath("$.data.inAppEnabled").value(false))
                    .andExpect(jsonPath("$.data.websocketEnabled").value(false))
                    .andExpect(jsonPath("$.data.emailEnabled").value(false))
                    .andExpect(jsonPath("$.data.digestEnabled").value(true));
        }
    }

    @Test
    @DisplayName("非法作者 ID 更新订阅时应该命中路径参数校验")
    void shouldRejectInvalidAuthorId() throws Exception {
        NotificationPreferenceController controller = new NotificationPreferenceController(
                notificationPreferenceCommandService,
                notificationPreferenceQueryService
        );
        var method = NotificationPreferenceController.class.getMethod(
                "updateAuthorSubscription",
                Long.class,
                com.zhicore.notification.interfaces.dto.request.UpdateAuthorSubscriptionRequest.class
        );
        var violations = executableValidator.validateParameters(
                controller,
                method,
                new Object[]{0L, null}
        );

        org.junit.jupiter.api.Assertions.assertEquals(2, violations.size());
    }
}
