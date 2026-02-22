package com.zhicore.admin.infrastructure.feign;

import com.zhicore.common.result.ApiResponse;
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
    
    @Override
    public IdGeneratorClient create(Throwable cause) {
        log.error("ID生成服务调用失败，触发降级", cause);
        
        return new IdGeneratorClient() {
            @Override
            public ApiResponse<Long> generateSnowflakeId() {
                log.error("生成Snowflake ID失败，降级处理", cause);
                return ApiResponse.fail(500, "ID生成服务暂时不可用");
            }
            
            @Override
            public ApiResponse<List<Long>> generateBatchSnowflakeIds(int count) {
                log.error("批量生成Snowflake ID失败，降级处理: count={}", count, cause);
                return ApiResponse.fail(500, "ID生成服务暂时不可用");
            }
            
            @Override
            public ApiResponse<Long> generateSegmentId(String bizTag) {
                log.error("生成Segment ID失败，降级处理: bizTag={}", bizTag, cause);
                return ApiResponse.fail(500, "ID生成服务暂时不可用");
            }
        };
    }
}
