package com.zhicore.content;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.result.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 测试环境下的 Feign 客户端桩配置
 *
 * 目的：让集成测试不依赖外部 ID 服务（zhicore-id-generator），避免网络/注册中心等外部因素导致不稳定。
 *
 * 说明：
 * - 仅在 test profile 生效；
 * - 使用 @Primary 覆盖 @FeignClient 生成的同类型 Bean；
 * - ID 采用进程内自增，满足“唯一且可预测”的测试需求。
 */
@Configuration
@Profile("test")
public class TestIdGeneratorStubConfiguration {

    @Bean
    @Primary
    public IdGeneratorFeignClient idGeneratorFeignClient() {
        AtomicLong sequence = new AtomicLong(1_000_000L);

        return new IdGeneratorFeignClient() {
            @Override
            public ApiResponse<Long> generateSnowflakeId() {
                return ApiResponse.success(sequence.incrementAndGet());
            }

            @Override
            public ApiResponse<List<Long>> generateBatchSnowflakeIds(int count) {
                if (count <= 0) {
                    return ApiResponse.success(List.of());
                }

                List<Long> ids = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    ids.add(sequence.incrementAndGet());
                }
                return ApiResponse.success(ids);
            }

            @Override
            public ApiResponse<Long> generateSegmentId(String bizTag) {
                return ApiResponse.success(sequence.incrementAndGet());
            }
        };
    }
}
