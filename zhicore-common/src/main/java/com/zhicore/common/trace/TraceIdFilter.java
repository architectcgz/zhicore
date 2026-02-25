package com.zhicore.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * TraceId 过滤器
 * 
 * 从请求头获取 TraceId，如果不存在则生成新的
 * 将 TraceId 放入 MDC 以便日志输出
 *
 * @author ZhiCore Team
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 尝试从请求头获取 TraceId
            String traceId = request.getHeader(TRACE_ID_HEADER);

            // 如果没有，尝试从 SkyWalking 获取
            if (!StringUtils.hasText(traceId)) {
                traceId = getSkyWalkingTraceId();
            }

            // 如果还是没有，生成新的
            if (!StringUtils.hasText(traceId)) {
                traceId = generateTraceId();
            }

            // 放入 MDC
            MDC.put(TRACE_ID_MDC_KEY, traceId);

            // 设置响应头
            response.setHeader(TRACE_ID_HEADER, traceId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    /**
     * 尝试从 SkyWalking 获取 TraceId
     */
    private String getSkyWalkingTraceId() {
        try {
            // 使用反射避免强依赖 SkyWalking
            Class<?> traceContextClass = Class.forName("org.apache.skywalking.apm.toolkit.trace.TraceContext");
            Object traceId = traceContextClass.getMethod("traceId").invoke(null);
            if (traceId != null && !"N/A".equals(traceId.toString())) {
                return traceId.toString();
            }
        } catch (Exception e) {
            // SkyWalking 未启用，忽略
        }
        return null;
    }

    /**
     * 生成 TraceId
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
