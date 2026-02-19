package com.blog.api.client;

import com.blog.api.client.fallback.IdGeneratorFeignClientFallbackFactory;
import com.blog.common.result.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * ID生成服务Feign客户端
 * 
 * 服务名：blog-id-generator
 * 提供Snowflake和Segment两种模式的ID生成
 */
@FeignClient(
    name = "blog-id-generator",
    path = "/api/v1/id",
    fallbackFactory = IdGeneratorFeignClientFallbackFactory.class
)
public interface IdGeneratorFeignClient {
    
    /**
     * 生成单个Snowflake ID
     */
    @GetMapping("/snowflake")
    ApiResponse<Long> generateSnowflakeId();
    
    /**
     * 批量生成Snowflake ID
     * 
     * @param count 生成数量，范围1-1000
     */
    @GetMapping("/snowflake/batch")
    ApiResponse<List<Long>> generateBatchSnowflakeIds(@RequestParam("count") int count);
    
    /**
     * 生成Segment ID
     * 
     * @param bizTag 业务标签
     */
    @GetMapping("/segment/{bizTag}")
    ApiResponse<Long> generateSegmentId(@PathVariable("bizTag") String bizTag);
}
