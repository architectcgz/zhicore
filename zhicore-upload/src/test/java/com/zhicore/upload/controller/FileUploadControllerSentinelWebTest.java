package com.zhicore.upload.controller;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.SentinelWebInterceptor;
import com.alibaba.csp.sentinel.adapter.spring.webmvc.config.SentinelWebMvcConfig;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.fileservice.client.model.AccessLevel;
import com.zhicore.common.exception.GlobalExceptionHandler;
import com.zhicore.common.sentinel.web.ApiResponseBlockExceptionHandler;
import com.zhicore.upload.infrastructure.sentinel.UploadRoutes;
import com.zhicore.upload.model.FileUploadResponse;
import com.zhicore.upload.service.FileUploadService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileUploadController Sentinel Web 回归测试")
class FileUploadControllerSentinelWebTest {

    @Mock
    private FileUploadService fileUploadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FileUploadController controller = new FileUploadController(fileUploadService);
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
    @DisplayName("上传图片接口命中 Sentinel 后应该返回 429 和统一响应体")
    void shouldReturnTooManyRequestsWhenUploadImageBlocked() throws Exception {
        FlowRuleManager.loadRules(List.of(buildUrlRule(UploadRoutes.IMAGE)));
        when(fileUploadService.uploadImage(any(), eq(AccessLevel.PUBLIC))).thenReturn(FileUploadResponse.builder()
                .fileId("file-1")
                .url("https://cdn.example.com/file-1.png")
                .accessLevel(AccessLevel.PUBLIC)
                .build());

        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", new byte[]{1, 2, 3});

        mockMvc.perform(multipart(UploadRoutes.IMAGE).file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(multipart(UploadRoutes.IMAGE).file(file))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("请求过于频繁"));

        verify(fileUploadService, times(1)).uploadImage(any(), eq(AccessLevel.PUBLIC));
    }

    private FlowRule buildUrlRule(String resource) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(1)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
    }
}
