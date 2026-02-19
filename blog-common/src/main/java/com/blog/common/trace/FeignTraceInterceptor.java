package com.blog.common.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Feign TraceId 传递拦截器
 * 
 * 在 Feign 调用时自动传递 TraceId
 *
 * @author Blog Team
 */
@Component
public class FeignTraceInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_MDC_KEY);
        if (StringUtils.hasText(traceId)) {
            template.header(TraceIdFilter.TRACE_ID_HEADER, traceId);
        }
    }
}
