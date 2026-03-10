package com.zhicore.upload.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.zhicore.upload.infrastructure.sentinel.UploadRoutes;
import com.zhicore.upload.service.sentinel.UploadSentinelResources;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("UploadSentinelConfig 测试")
class UploadSentinelConfigTest {

    @AfterEach
    void tearDown() {
        FlowRuleManager.loadRules(Collections.emptyList());
    }

    @Test
    @DisplayName("应该加载上传服务热点规则")
    void shouldLoadUploadRules() {
        UploadSentinelProperties properties = new UploadSentinelProperties();
        UploadSentinelConfig config = new UploadSentinelConfig(properties);

        config.initFlowRules();

        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UploadRoutes.IMAGE.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UploadSentinelResources.UPLOAD_IMAGE.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UploadSentinelResources.GET_FILE_URL.equals(rule.getResource())));
        assertTrue(FlowRuleManager.getRules().stream()
                .anyMatch(rule -> UploadSentinelResources.DELETE_FILE.equals(rule.getResource())));
    }

    @Test
    @DisplayName("重复初始化时应该按资源覆盖而不是重复追加")
    void shouldReplaceRulesByResource() {
        UploadSentinelProperties properties = new UploadSentinelProperties();
        UploadSentinelConfig config = new UploadSentinelConfig(properties);

        config.initFlowRules();
        config.initFlowRules();

        long imageRouteRules = FlowRuleManager.getRules().stream()
                .filter(rule -> UploadRoutes.IMAGE.equals(rule.getResource()))
                .count();
        long getFileUrlRules = FlowRuleManager.getRules().stream()
                .filter(rule -> UploadSentinelResources.GET_FILE_URL.equals(rule.getResource()))
                .count();

        assertEquals(1L, imageRouteRules);
        assertEquals(1L, getFileUrlRules);
    }
}
