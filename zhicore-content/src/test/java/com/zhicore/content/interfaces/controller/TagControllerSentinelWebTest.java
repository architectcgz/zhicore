package com.zhicore.content.interfaces.controller;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.SentinelWebInterceptor;
import com.alibaba.csp.sentinel.adapter.spring.webmvc.config.SentinelWebMvcConfig;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.sentinel.web.ApiResponseBlockExceptionHandler;
import com.zhicore.content.application.service.TagQueryFacade;
import com.zhicore.content.infrastructure.sentinel.ContentRoutes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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
@DisplayName("TagQueryController Sentinel Web 回归测试")
class TagControllerSentinelWebTest {

    @Mock
    private TagQueryFacade tagQueryFacade;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TagQueryController controller = new TagQueryController(tagQueryFacade);
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
    @DisplayName("热门标签接口命中 Sentinel 后应该返回 429 和统一响应体")
    void shouldReturnTooManyRequestsWhenHotTagsBlocked() throws Exception {
        FlowRuleManager.loadRules(List.of(buildUrlRule(ContentRoutes.TAGS_HOT)));
        when(tagQueryFacade.getHotTags(10)).thenReturn(List.of());

        mockMvc.perform(get(ContentRoutes.TAGS_HOT).param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get(ContentRoutes.TAGS_HOT).param("limit", "10"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("请求过于频繁"));

        verify(tagQueryFacade, times(1)).getHotTags(10);
    }

    private FlowRule buildUrlRule(String resource) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
    }
}
