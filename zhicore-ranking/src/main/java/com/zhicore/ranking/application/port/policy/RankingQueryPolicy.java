package com.zhicore.ranking.application.port.policy;

import java.time.Duration;
import java.time.LocalDate;

/**
 * 排行榜查询策略端口。
 *
 * 封装查询路径所需的缓存窗口、回填规模和锁超时配置，
 * 避免 application 直接依赖基础设施配置实现。
 */
public interface RankingQueryPolicy {

    boolean isMonthlyQueryInRedisRange(LocalDate queryDate, LocalDate now);

    int monthlyBackfillMaxSize();

    Duration monthlyLoadLockWaitTime();

    Duration monthlyLoadLockLeaseTime();
}
