package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.ResultCode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ID生成服务 Feign 客户端降级工厂
 */
@Slf4j
@Component
public class IdGeneratorClientFallbackFactory implements FallbackFactory<IdGeneratorClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public IdGeneratorClientFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-id-generator");
    }

    @Override
    public IdGeneratorClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);

        return new IdGeneratorClient() {
            @Override
            public ApiResponse<Long> generateSnowflakeId() {
                log.warn("Admin IdGeneratorClient.generateSnowflakeId fallback triggered: cause={}",
                        fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("ID生成服务暂时不可用");
            }
            
            @Override
            public ApiResponse<List<Long>> generateBatchSnowflakeIds(int count) {
                log.warn("Admin IdGeneratorClient.generateBatchSnowflakeIds fallback triggered: count={}, cause={}",
                        count, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("ID生成服务暂时不可用");
            }
            
            @Override
            public ApiResponse<Long> generateSegmentId(String bizTag) {
                log.warn("Admin IdGeneratorClient.generateSegmentId fallback triggered: bizTag={}, cause={}",
                        bizTag, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("ID生成服务暂时不可用");
            }
        };
    }
}
