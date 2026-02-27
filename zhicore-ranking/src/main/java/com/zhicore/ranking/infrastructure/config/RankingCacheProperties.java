package com.zhicore.ranking.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 排行榜缓存配置属性
 *
 * 控制排行榜查询服务的缓存行为，包括 Redis TTL、回填参数、锁超时等
 */
@Data
@Component
@ConfigurationProperties(prefix = "zhicore.ranking.cache")
public class RankingCacheProperties {

    /** 月榜在 Redis 中保留的天数，超出范围的查询直接走 MongoDB */
    private int monthlyRedisTtlDays = 365;

    /** 回填 Redis 时从 MongoDB 拉取的上限 */
    private int backfillMaxSize = 1000;

    /** 空结果缓存时长（秒），防止缓存穿透 */
    private long emptyCacheTtlSeconds = 30;

    /** 回填锁等待时间（秒） */
    private long lockWaitSeconds = 5;

    /** 回填锁持有时间（秒） */
    private long lockLeaseSeconds = 30;
}
