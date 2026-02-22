package com.zhicore.idgenerator.service;

import java.util.List;

/**
 * ID生成服务接口
 * 
 * 封装id-generator-client，提供ID生成能力
 */
public interface IdGeneratorService {
    
    /**
     * 生成单个Snowflake ID
     * 
     * @return Snowflake ID
     */
    Long generateSnowflakeId();
    
    /**
     * 批量生成Snowflake ID
     * 
     * @param count 生成数量，范围1-1000
     * @return Snowflake ID列表
     * @throws IllegalArgumentException 当count不在有效范围内时
     */
    List<Long> generateBatchSnowflakeIds(int count);
    
    /**
     * 生成Segment ID
     * 
     * @param bizTag 业务标签
     * @return Segment ID
     * @throws IllegalArgumentException 当bizTag为空时
     */
    Long generateSegmentId(String bizTag);
}
