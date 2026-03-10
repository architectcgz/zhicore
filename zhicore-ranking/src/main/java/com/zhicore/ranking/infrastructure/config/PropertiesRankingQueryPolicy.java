package com.zhicore.ranking.infrastructure.config;

import com.zhicore.ranking.application.port.policy.RankingQueryPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 基于配置属性的排行榜查询策略实现。
 */
@Component
@RequiredArgsConstructor
public class PropertiesRankingQueryPolicy implements RankingQueryPolicy {

    private final RankingCacheProperties cacheProperties;

    @Override
    public boolean isMonthlyQueryInRedisRange(LocalDate queryDate, LocalDate now) {
        return queryDate.isAfter(now.minusDays(cacheProperties.getMonthlyRedisTtlDays()));
    }

    @Override
    public int monthlyBackfillMaxSize() {
        return cacheProperties.getBackfillMaxSize();
    }

    @Override
    public Duration monthlyLoadLockWaitTime() {
        return Duration.ofSeconds(cacheProperties.getLockWaitSeconds());
    }

    @Override
    public Duration monthlyLoadLockLeaseTime() {
        return Duration.ofSeconds(cacheProperties.getLockLeaseSeconds());
    }
}
