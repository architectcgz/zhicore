package com.zhicore.common.sentinel.web;

import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("ApiResponseBlockExceptionHandler 测试")
class ApiResponseBlockExceptionHandlerTest {

    @Test
    @DisplayName("Sentinel block 时应该返回 429 和统一响应体")
    void shouldReturnTooManyRequestsResponse() throws Exception {
        ApiResponseBlockExceptionHandler handler = new ApiResponseBlockExceptionHandler(new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/search/posts");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new FlowException("blocked"));

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("\"code\":429"));
        assertTrue(response.getContentAsString().contains("请求过于频繁"));
    }
}
