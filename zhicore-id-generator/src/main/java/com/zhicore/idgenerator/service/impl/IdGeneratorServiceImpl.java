package com.zhicore.idgenerator.service.impl;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.idgenerator.service.IdGeneratorService;
import com.zhicore.idgenerator.service.sentinel.IdGeneratorSentinelHandlers;
import com.zhicore.idgenerator.service.sentinel.IdGeneratorSentinelResources;
import com.platform.idgen.client.IdGeneratorClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ID生成服务实现
 * 
 * 通过id-generator-client调用底层ID生成服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdGeneratorServiceImpl implements IdGeneratorService {

    /** 批量生成 ID 的最大数量 */
    private static final int MAX_BATCH_SIZE = 1000;

    private final IdGeneratorClient idGeneratorClient;
    
    @Override
    @SentinelResource(
            value = IdGeneratorSentinelResources.GENERATE_SNOWFLAKE_ID,
            blockHandlerClass = IdGeneratorSentinelHandlers.class,
            blockHandler = "handleGenerateSnowflakeIdBlocked"
    )
    public Long generateSnowflakeId() {
        try {
            Long id = idGeneratorClient.nextSnowflakeId();
            if (id == null) {
                throw new BusinessException("ID生成失败：返回值为null");
            }
            log.debug("成功生成Snowflake ID: {}", id);
            return id;
        } catch (Exception e) {
            log.error("生成Snowflake ID失败", e);
            throw new BusinessException("ID生成失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @SentinelResource(
            value = IdGeneratorSentinelResources.GENERATE_BATCH_SNOWFLAKE_IDS,
            blockHandlerClass = IdGeneratorSentinelHandlers.class,
            blockHandler = "handleGenerateBatchSnowflakeIdsBlocked"
    )
    public List<Long> generateBatchSnowflakeIds(int count) {
        // 参数验证
        if (count <= 0 || count > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("生成数量必须在1-" + MAX_BATCH_SIZE + "之间");
        }
        
        try {
            // 使用 IdGeneratorClient 的批量接口，更高效
            List<Long> ids = idGeneratorClient.nextSnowflakeIds(count);
            if (ids == null || ids.isEmpty()) {
                throw new BusinessException("批量生成ID失败：返回值为空");
            }
            if (ids.size() != count) {
                throw new BusinessException("批量生成ID失败：返回数量不匹配，期望" + count + "个，实际" + ids.size() + "个");
            }
            log.debug("成功批量生成{}个Snowflake ID", count);
            return ids;
        } catch (IllegalArgumentException e) {
            // 参数异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("批量生成Snowflake ID失败: count={}", count, e);
            throw new BusinessException("批量ID生成失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    @SentinelResource(
            value = IdGeneratorSentinelResources.GENERATE_SEGMENT_ID,
            blockHandlerClass = IdGeneratorSentinelHandlers.class,
            blockHandler = "handleGenerateSegmentIdBlocked"
    )
    public Long generateSegmentId(String bizTag) {
        // 参数验证
        if (bizTag == null || bizTag.isBlank()) {
            throw new IllegalArgumentException("业务标签不能为空");
        }
        
        try {
            Long id = idGeneratorClient.nextSegmentId(bizTag);
            if (id == null) {
                throw new BusinessException("ID生成失败：返回值为null");
            }
            log.debug("成功生成Segment ID: bizTag={}, id={}", bizTag, id);
            return id;
        } catch (IllegalArgumentException e) {
            // 参数异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("生成Segment ID失败: bizTag={}", bizTag, e);
            throw new BusinessException("ID生成失败: " + e.getMessage(), e);
        }
    }
}
