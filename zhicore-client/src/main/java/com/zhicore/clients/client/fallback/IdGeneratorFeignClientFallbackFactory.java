package com.zhicore.api.client.fallback;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ID生成服务降级处理
 */
@Slf4j
@Component
public class IdGeneratorFeignClientFallbackFactory implements FallbackFactory<IdGeneratorFeignClient> {

    private final DownstreamFallbackSupport fallbackSupport = new DownstreamFallbackSupport("zhicore-id-generator");

    @Override
    public IdGeneratorFeignClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new IdGeneratorFeignClient() {
            @Override
            public ApiResponse<Long> generateSnowflakeId() {
                log.warn("IdGeneratorFeignClient.generateSnowflakeId fallback triggered: cause={}",
                        fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("ID生成服务暂时不可用，请稍后重试");
            }
            
            @Override
            public ApiResponse<List<Long>> generateBatchSnowflakeIds(int count) {
                log.warn("IdGeneratorFeignClient.generateBatchSnowflakeIds fallback triggered: count={}, cause={}",
                        count, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("ID生成服务暂时不可用，请稍后重试");
            }
            
            @Override
            public ApiResponse<Long> generateSegmentId(String bizTag) {
                log.warn("IdGeneratorFeignClient.generateSegmentId fallback triggered: bizTag={}, cause={}",
                        bizTag, fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("ID生成服务暂时不可用，请稍后重试");
            }
        };
    }
}
