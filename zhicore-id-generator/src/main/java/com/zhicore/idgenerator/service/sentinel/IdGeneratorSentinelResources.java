package com.zhicore.idgenerator.service.sentinel;

/**
 * ID 生成服务 Sentinel 方法级资源常量。
 */
public final class IdGeneratorSentinelResources {

    private IdGeneratorSentinelResources() {
    }

    public static final String GENERATE_SNOWFLAKE_ID = "id-generator:generateSnowflakeId";
    public static final String GENERATE_BATCH_SNOWFLAKE_IDS = "id-generator:generateBatchSnowflakeIds";
    public static final String GENERATE_SEGMENT_ID = "id-generator:generateSegmentId";
}
