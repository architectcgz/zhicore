package com.zhicore.api.client.fallback;

import com.zhicore.api.client.IdGeneratorFeignClient;
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
    
    @Override
    public IdGeneratorFeignClient create(Throwable cause) {
        return new IdGeneratorFeignClient() {
            @Override
            public ApiResponse<Long> generateSnowflakeId() {
                log.error("调用ID生成服务失败，触发降级", cause);
                return ApiResponse.fail(503, "ID生成服务暂时不可用，请稍后重试");
            }
            
            @Override
            public ApiResponse<List<Long>> generateBatchSnowflakeIds(int count) {
                log.error("批量调用ID生成服务失败，触发降级: count={}", count, cause);
                return ApiResponse.fail(503, "ID生成服务暂时不可用，请稍后重试");
            }
            
            @Override
            public ApiResponse<Long> generateSegmentId(String bizTag) {
                log.error("调用Segment ID生成服务失败，触发降级: bizTag={}", bizTag, cause);
                return ApiResponse.fail(503, "ID生成服务暂时不可用，请稍后重试");
            }
        };
    }
}
