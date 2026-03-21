package com.zhicore.comment.infrastructure.feign;

import com.zhicore.comment.application.dto.RankingHotPostCandidatesResponse;
import com.zhicore.common.feign.DownstreamFallbackSupport;
import com.zhicore.common.result.ApiResponse;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * ranking 服务降级工厂。
 */
@Slf4j
@Component
public class RankingServiceFallbackFactory implements FallbackFactory<RankingServiceClient> {

    private final DownstreamFallbackSupport fallbackSupport;

    public RankingServiceFallbackFactory(MeterRegistry meterRegistry) {
        this.fallbackSupport = new DownstreamFallbackSupport(meterRegistry, "zhicore-ranking");
    }

    @Override
    public RankingServiceClient create(Throwable cause) {
        fallbackSupport.onFallbackTriggered(log, cause);
        return new RankingServiceClient() {
            @Override
            public ApiResponse<RankingHotPostCandidatesResponse> getHotPostCandidates() {
                log.warn("RankingService.getHotPostCandidates fallback triggered, cause={}",
                        fallbackSupport.failureMessage(cause));
                return fallbackSupport.degraded("排行榜服务已降级");
            }
        };
    }
}
