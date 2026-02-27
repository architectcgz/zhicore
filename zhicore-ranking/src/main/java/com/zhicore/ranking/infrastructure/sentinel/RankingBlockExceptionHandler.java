package com.zhicore.ranking.infrastructure.sentinel;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 排行榜限流全局异常处理
 *
 * <p>Sentinel Web 适配器拦截到限流/熔断时，统一返回 429 + ApiResponse 格式。</p>
 */
@Slf4j
@Component
public class RankingBlockExceptionHandler implements BlockExceptionHandler {

    private final ObjectMapper objectMapper;

    public RankingBlockExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       BlockException ex) throws Exception {
        log.warn("排行榜查询被限流: uri={}, resource={}, rule={}",
                request.getRequestURI(),
                ex.getRule() != null ? ex.getRule().getResource() : "unknown",
                ex.getRule());

        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Void> result = ApiResponse.fail(ResultCode.TOO_MANY_REQUESTS);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
