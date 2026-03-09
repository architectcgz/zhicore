package com.zhicore.common.sentinel.web;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.AbstractRule;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Sentinel Web 限流/熔断统一异常处理。
 *
 * <p>所有服务在触发 Sentinel Block 时统一返回 429 + ApiResponse，
 * 避免各模块重复实现不同格式的 BlockExceptionHandler。</p>
 */
@Slf4j
@Component
@ConditionalOnMissingBean(BlockExceptionHandler.class)
public class ApiResponseBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    public ApiResponseBlockExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, BlockException ex) throws Exception {
        Object rule = safeRule(ex);
        log.warn("Sentinel block triggered: uri={}, resource={}, rule={}",
                request.getRequestURI(),
                rule instanceof AbstractRule abstractRule ? abstractRule.getResource() : "unknown",
                rule);

        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.fail(ResultCode.TOO_MANY_REQUESTS)));
    }

    private Object safeRule(BlockException ex) {
        try {
            return ex.getRule();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
