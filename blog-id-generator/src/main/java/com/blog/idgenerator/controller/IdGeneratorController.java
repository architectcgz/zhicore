package com.blog.idgenerator.controller;

import com.blog.common.result.ApiResponse;
import com.blog.idgenerator.service.IdGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ID生成服务控制器
 * 
 * 提供统一的分布式ID生成接口，包括Snowflake ID和Segment ID两种模式
 * 
 * @author Blog Team
 */
@Tag(name = "ID生成服务", description = "提供Snowflake和Segment两种模式的分布式ID生成")
@Slf4j
@RestController
@RequestMapping("/api/v1/id")
@RequiredArgsConstructor
public class IdGeneratorController {
    
    private final IdGeneratorService idGeneratorService;
    
    /**
     * 生成单个Snowflake ID
     * 
     * @return Snowflake ID
     */
    @Operation(
            summary = "生成Snowflake ID",
            description = "生成单个Snowflake ID，适用于需要全局唯一标识的场景"
    )
    @GetMapping("/snowflake")
    public ApiResponse<Long> generateSnowflakeId() {
        log.debug("收到生成Snowflake ID请求");
        
        Long id = idGeneratorService.generateSnowflakeId();
        
        log.debug("成功生成Snowflake ID: {}", id);
        return ApiResponse.success(id);
    }
    
    /**
     * 批量生成Snowflake ID
     * 
     * @param count 生成数量，范围1-1000
     * @return Snowflake ID列表
     */
    @Operation(
            summary = "批量生成Snowflake ID",
            description = "批量生成指定数量的Snowflake ID，适用于需要批量创建实体的场景。单次最多生成1000个ID"
    )
    @GetMapping("/snowflake/batch")
    public ApiResponse<List<Long>> generateBatchSnowflakeIds(
            @Parameter(description = "生成数量，范围1-1000", required = true, example = "10")
            @RequestParam("count") int count) {
        log.debug("收到批量生成Snowflake ID请求: count={}", count);
        
        List<Long> ids = idGeneratorService.generateBatchSnowflakeIds(count);
        
        log.debug("成功批量生成{}个Snowflake ID", ids.size());
        return ApiResponse.success(ids);
    }
    
    /**
     * 生成Segment ID
     * 
     * @param bizTag 业务标签
     * @return Segment ID
     */
    @Operation(
            summary = "生成Segment ID",
            description = "根据业务标签生成Segment ID，适用于需要连续ID的场景。不同的业务标签使用独立的ID序列"
    )
    @GetMapping("/segment/{bizTag}")
    public ApiResponse<Long> generateSegmentId(
            @Parameter(description = "业务标签，用于区分不同业务的ID序列", required = true, example = "user")
            @PathVariable("bizTag") String bizTag) {
        log.debug("收到生成Segment ID请求: bizTag={}", bizTag);
        
        Long id = idGeneratorService.generateSegmentId(bizTag);
        
        log.debug("成功生成Segment ID: bizTag={}, id={}", bizTag, id);
        return ApiResponse.success(id);
    }
}
