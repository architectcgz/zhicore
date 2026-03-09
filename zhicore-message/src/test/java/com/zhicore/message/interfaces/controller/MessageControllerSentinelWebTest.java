package com.zhicore.message.interfaces.controller;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.SentinelWebInterceptor;
import com.alibaba.csp.sentinel.adapter.spring.webmvc.config.SentinelWebMvcConfig;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.context.UserContext;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.sentinel.web.ApiResponseBlockExceptionHandler;
import com.zhicore.message.application.service.MessageApplicationService;
import com.zhicore.message.infrastructure.sentinel.MessageRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController Sentinel Web 回归测试")
class MessageControllerSentinelWebTest {

    @Mock
    private MessageApplicationService messageApplicationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MessageController controller = new MessageController(messageApplicationService);
        SentinelWebMvcConfig config = new SentinelWebMvcConfig();
        config.setBlockExceptionHandler(new ApiResponseBlockExceptionHandler(new ObjectMapper()));

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(new SentinelWebInterceptor(config))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("未读数接口命中 Sentinel 后应该返回 429 和统一响应体")
    void shouldReturnTooManyRequestsWhenUnreadCountBlocked() throws Exception {
        FlowRuleManager.loadRules(List.of(buildUrlRule(MessageRoutes.UNREAD_COUNT)));
        when(messageApplicationService.getUnreadCount()).thenReturn(3);

        try (MockedStatic<UserContext> userContext = org.mockito.Mockito.mockStatic(UserContext.class)) {
            userContext.when(UserContext::requireUserId).thenReturn(1001L);

            mockMvc.perform(get(MessageRoutes.UNREAD_COUNT))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            mockMvc.perform(get(MessageRoutes.UNREAD_COUNT))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value(429))
                    .andExpect(jsonPath("$.message").value("请求过于频繁"));
        }

        verify(messageApplicationService, times(1)).getUnreadCount();
    }

    private FlowRule buildUrlRule(String resource) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
    }
}
