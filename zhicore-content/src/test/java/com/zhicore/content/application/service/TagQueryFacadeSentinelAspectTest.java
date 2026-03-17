package com.zhicore.content.application.service;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.common.exception.TooManyRequestsException;
import com.zhicore.common.sentinel.SentinelAspectConfig;
import com.zhicore.content.application.query.TagQuery;
import com.zhicore.content.application.service.query.TagListQueryService;
import com.zhicore.content.application.service.query.TagPostQueryService;
import com.zhicore.content.application.sentinel.ContentSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(TagQueryFacadeSentinelAspectTest.TestConfig.class)
@DisplayName("@SentinelResource 注解限流集成测试")
class TagQueryFacadeSentinelAspectTest {

    @jakarta.annotation.Resource
    private TagQueryFacade tagQueryFacade;

    @jakarta.annotation.Resource
    private TagQuery tagQuery;

    @jakarta.annotation.Resource
    private TagListQueryService tagListQueryService;

    @jakarta.annotation.Resource
    private TagPostQueryService tagPostQueryService;

    @BeforeEach
    void setUp() {
        reset(tagQuery, tagListQueryService, tagPostQueryService);
        when(tagQuery.getHotTags(anyInt())).thenReturn(Collections.emptyList());
        FlowRuleManager.loadRules(List.of(new FlowRule(ContentSentinelResources.GET_HOT_TAGS)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT)));
    }

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("同一资源超过阈值时应该命中 blockHandler")
    void shouldBlockSecondInvocationWhenRuleExceeded() {
        assertDoesNotThrow(() -> tagQueryFacade.getHotTags(10));
        assertThrows(TooManyRequestsException.class, () -> tagQueryFacade.getHotTags(10));
    }

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import(SentinelAspectConfig.class)
    static class TestConfig {

        @Bean
        TagQuery tagQuery() {
            return mock(TagQuery.class);
        }

        @Bean
        TagListQueryService tagListQueryService() {
            return mock(TagListQueryService.class);
        }

        @Bean
        TagPostQueryService tagPostQueryService() {
            return mock(TagPostQueryService.class);
        }

        @Bean
        TagQueryFacade tagQueryFacade(TagQuery tagQuery,
                                      TagListQueryService tagListQueryService,
                                      TagPostQueryService tagPostQueryService) {
            return new TagQueryFacade(tagQuery, tagListQueryService, tagPostQueryService);
        }
    }
}
