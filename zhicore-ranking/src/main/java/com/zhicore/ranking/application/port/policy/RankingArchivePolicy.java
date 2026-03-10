package com.zhicore.ranking.application.port.policy;

/**
 * 排行榜归档策略端口。
 */
public interface RankingArchivePolicy {

    int archiveLimit();

    String dailyArchiveLockKey();

    String weeklyArchiveLockKey();

    String monthlyArchiveLockKey();
}
