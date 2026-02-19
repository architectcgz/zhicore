package com.blog.admin.infrastructure.feign;

import com.blog.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * ID生成服务 Feign 客户端
 * 
 * 调用 blog-id-generator 服务生成分布式ID
 */
@FeignClient(
        name = "blog-id-generator",
        contextId = "idGeneratorClient",
        fallbackFactory = IdGeneratorClientFallbackFactory.class
)
public interface IdGeneratorClient {
    
    /**
     * 生成单个 Snowflake ID
     * 
     * @return Snowflake ID
     */
    @GetMapping("/api/v1/id/snowflake")
    ApiResponse<Long> generateSnowflakeId();
    
    /**
     * 批量生成 Snowflake ID
     * 
     * @param count 生成数量，范围1-1000
     * @return Snowflake ID 列表
     */
    @GetMapping("/api/v1/id/snowflake/batch")
    ApiResponse<List<Long>> generateBatchSnowflakeIds(@RequestParam("count") int count);
    
    /**
     * 生成 Segment ID
     * 
     * @param bizTag 业务标签
     * @return Segment ID
     */
    @GetMapping("/api/v1/id/segment/{bizTag}")
    ApiResponse<Long> generateSegmentId(@PathVariable("bizTag") String bizTag);
}
