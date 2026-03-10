package com.zhicore.idgenerator.service.sentinel;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.zhicore.common.exception.TooManyRequestsException;

import java.util.List;

/**
 * ID 生成服务 Sentinel block 处理器。
 */
public final class IdGeneratorSentinelHandlers {

    private IdGeneratorSentinelHandlers() {
    }

    public static Long handleGenerateSnowflakeIdBlocked(BlockException ex) {
        throw tooManyRequests("Snowflake ID 生成请求过于频繁，请稍后重试");
    }

    public static List<Long> handleGenerateBatchSnowflakeIdsBlocked(int count, BlockException ex) {
        throw tooManyRequests("批量 Snowflake ID 生成请求过于频繁，请稍后重试");
    }

    public static Long handleGenerateSegmentIdBlocked(String bizTag, BlockException ex) {
        throw tooManyRequests("Segment ID 生成请求过于频繁，请稍后重试");
    }

    private static TooManyRequestsException tooManyRequests(String message) {
        return new TooManyRequestsException(message);
    }
}
