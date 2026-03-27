package com.zhicore.notification.interfaces.controller;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.UnauthorizedException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.test.web.ControllerTestSupport;
import com.zhicore.notification.application.dto.AggregatedNotificationVO;
import com.zhicore.notification.application.service.NotificationAggregationService;
import com.zhicore.notification.application.service.command.NotificationCommandService;
import com.zhicore.notification.application.service.query.NotificationQueryService;
import com.zhicore.notification.domain.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("Notification query/command controller 测试")
class NotificationControllerTest extends ControllerTestSupport {

    @Mock
    private NotificationCommandService notificationCommandService;

    @Mock
    private NotificationQueryService notificationQueryService;

    @Mock
    private NotificationAggregationService notificationAggregationService;

    @Test
    @DisplayName("获取聚合通知列表成功时应该返回分页结果")
    void shouldReturnAggregatedNotifications() throws Exception {
        NotificationQueryController controller = new NotificationQueryController(
                notificationQueryService,
                notificationAggregationService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        AggregatedNotificationVO item = AggregatedNotificationVO.builder()
                .type(NotificationType.LIKE)
                .targetType("post")
                .targetId(1234567890123456789L)
                .totalCount(3)
                .unreadCount(2)
                .recentActors(java.util.List.of(buildActor(2234567890123456789L, "张三")))
                .aggregatedContent("张三等3人赞了你的内容")
                .build();
        when(notificationAggregationService.getAggregatedNotifications(11L, 0, 20))
                .thenReturn(PageResult.of(0, 20, 1, java.util.List.of(item)));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notifications")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.records[0].type").value("LIKE"))
                    .andExpect(jsonPath("$.data.records[0].targetType").value("post"))
                    .andExpect(jsonPath("$.data.records[0].targetId").value("1234567890123456789"))
                    .andExpect(jsonPath("$.data.records[0].recentActors[0].id").value("2234567890123456789"))
                    .andExpect(jsonPath("$.data.records[0].aggregatedContent").value("张三等3人赞了你的内容"));
        }
    }

    @Test
    @DisplayName("未登录查询聚合通知时应该返回未授权")
    void shouldReturnUnauthorizedWhenGetNotificationsWithoutLogin() throws Exception {
        NotificationQueryController controller = new NotificationQueryController(
                notificationQueryService,
                notificationAggregationService
        );
        MockMvc mockMvc = buildMockMvc(controller);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenThrow(new UnauthorizedException("请先登录"));

            mockMvc.perform(get("/api/v1/notifications")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value(ResultCode.UNAUTHORIZED.getCode()))
                    .andExpect(jsonPath("$.message").value("请先登录"));
        }
    }

    @Test
    @DisplayName("聚合通知查询失败时应该返回业务错误响应")
    void shouldReturnBusinessErrorWhenGetNotificationsFails() throws Exception {
        NotificationQueryController controller = new NotificationQueryController(
                notificationQueryService,
                notificationAggregationService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        when(notificationAggregationService.getAggregatedNotifications(11L, 0, 20))
                .thenThrow(new BusinessException(ResultCode.SERVICE_DEGRADED, "通知聚合服务暂时不可用"));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notifications")
                            .param("page", "0")
                            .param("size", "20"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SERVICE_DEGRADED.getCode()))
                    .andExpect(jsonPath("$.message").value("通知聚合服务暂时不可用"));
        }
    }

    @Test
    @DisplayName("应该支持通过嵌套路由获取未读通知数量")
    void shouldGetUnreadCountViaNestedRoute() throws Exception {
        NotificationQueryController controller = new NotificationQueryController(
                notificationQueryService,
                notificationAggregationService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        when(notificationQueryService.getUnreadCount(11L)).thenReturn(7);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(get("/api/v1/notifications/unread/count"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data").value(7));
        }
    }

    @Test
    @DisplayName("标记通知已读失败时应该返回业务错误响应")
    void shouldReturnBusinessErrorWhenNotificationNotFound() throws Exception {
        NotificationCommandController controller = new NotificationCommandController(
                notificationCommandService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        doThrow(new BusinessException(ResultCode.NOTIFICATION_NOT_FOUND))
                .when(notificationCommandService).markAsRead(101L, 11L);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(post("/api/v1/notifications/{notificationId}/read", 101L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.NOTIFICATION_NOT_FOUND.getCode()))
                    .andExpect(jsonPath("$.message").value(ResultCode.NOTIFICATION_NOT_FOUND.getMessage()));
        }
    }

    @Test
    @DisplayName("读取他人通知时应该返回资源访问拒绝")
    void shouldReturnAccessDeniedWhenMarkingOthersNotificationAsRead() throws Exception {
        NotificationCommandController controller = new NotificationCommandController(
                notificationCommandService
        );
        MockMvc mockMvc = buildMockMvc(controller);
        doThrow(new BusinessException(ResultCode.RESOURCE_ACCESS_DENIED, "无权访问该通知"))
                .when(notificationCommandService).markAsRead(101L, 11L);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);

            mockMvc.perform(post("/api/v1/notifications/{notificationId}/read", 101L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.RESOURCE_ACCESS_DENIED.getCode()))
                    .andExpect(jsonPath("$.message").value("无权访问该通知"));
        }
    }

    @Test
    @DisplayName("通知ID非法时应该命中方法参数校验")
    void shouldRejectInvalidNotificationId() throws Exception {
        NotificationCommandController controller = new NotificationCommandController(notificationCommandService);
        var method = NotificationCommandController.class.getMethod("markAsRead", Long.class);
        var violations = executableValidator.validateParameters(controller, method, new Object[]{0L});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("通知ID必须为正数", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("分页参数非法时应该命中方法参数校验")
    void shouldRejectInvalidPageSize() throws Exception {
        NotificationQueryController controller = new NotificationQueryController(
                notificationQueryService,
                notificationAggregationService
        );
        var method = NotificationQueryController.class.getMethod("getNotifications", int.class, int.class);
        var violations = executableValidator.validateParameters(controller, method, new Object[]{-1, 0});

        org.junit.jupiter.api.Assertions.assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("超大分页大小时应该命中方法参数校验")
    void shouldRejectOversizedPageSize() throws Exception {
        NotificationQueryController controller = new NotificationQueryController(
                notificationQueryService,
                notificationAggregationService
        );
        var method = NotificationQueryController.class.getMethod("getNotifications", int.class, int.class);
        var violations = executableValidator.validateParameters(controller, method, new Object[]{0, 101});

        org.junit.jupiter.api.Assertions.assertEquals(1, violations.size());
        org.junit.jupiter.api.Assertions.assertEquals("每页数量不能大于100", violations.iterator().next().getMessage());
    }

    private UserSimpleDTO buildActor(Long userId, String nickname) {
        UserSimpleDTO actor = new UserSimpleDTO();
        actor.setId(userId);
        actor.setNickname(nickname);
        return actor;
    }
}
