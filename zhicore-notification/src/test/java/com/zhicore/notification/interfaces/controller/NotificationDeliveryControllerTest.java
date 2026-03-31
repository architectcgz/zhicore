package com.zhicore.notification.interfaces.controller;

import com.zhicore.common.context.UserContext;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.test.web.ControllerTestSupport;
import com.zhicore.notification.application.dto.NotificationDeliveryDTO;
import com.zhicore.notification.application.service.delivery.NotificationDeliveryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDeliveryController 测试")
class NotificationDeliveryControllerTest extends ControllerTestSupport {

    @Mock
    private NotificationDeliveryService notificationDeliveryService;

    @Test
    @DisplayName("查询 delivery 列表时应返回分页结果")
    void shouldReturnDeliveryPage() throws Exception {
        NotificationDeliveryController controller = new NotificationDeliveryController(notificationDeliveryService);
        MockMvc mockMvc = buildMockMvc(controller);
        NotificationDeliveryDTO delivery = NotificationDeliveryDTO.builder()
                .id(101L)
                .campaignId(201L)
                .recipientId(301L)
                .notificationId(401L)
                .channel("WEBSOCKET")
                .status("FAILED")
                .failureReason("SOCKET_DOWN")
                .retryCount(2)
                .createdAt(Instant.parse("2026-03-27T10:00:00Z"))
                .build();
        when(notificationDeliveryService.queryDeliveries(201L, 11L, "WEBSOCKET", "FAILED", 0, 20))
                .thenReturn(PageResult.of(0, 20, 1, List.of(delivery)));

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);
            userContext.when(UserContext::isAdmin).thenReturn(false);

            mockMvc.perform(get("/api/v1/notifications/deliveries")
                            .param("campaignId", "201")
                            .param("channel", "WEBSOCKET")
                            .param("status", "FAILED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()))
                    .andExpect(jsonPath("$.data.records[0].channel").value("WEBSOCKET"))
                    .andExpect(jsonPath("$.data.records[0].failureReason").value("SOCKET_DOWN"));
        }
    }

    @Test
    @DisplayName("重试 delivery 时应调用应用服务")
    void shouldRetryDelivery() throws Exception {
        NotificationDeliveryController controller = new NotificationDeliveryController(notificationDeliveryService);
        MockMvc mockMvc = buildMockMvc(controller);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(11L);
            userContext.when(UserContext::isAdmin).thenReturn(false);

            mockMvc.perform(post("/api/v1/notifications/deliveries/{deliveryId}/retry", 101L))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.SUCCESS.getCode()));
        }

        verify(notificationDeliveryService).retryDelivery(101L, 11L, false);
    }
}
