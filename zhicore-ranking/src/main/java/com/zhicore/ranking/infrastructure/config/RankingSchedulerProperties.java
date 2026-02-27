package com.zhicore.ranking.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 排行榜定时任务调度配置
 *
 * <p>所有 cron 表达式支持通过 Nacos 配置覆盖，修改后需重启生效。
 * 如需运行时动态生效，后续可改用 SchedulingConfigurer 方案。</p>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ranking.scheduler")
public class RankingSchedulerProperties {

    /** 热榜刷新 cron（默认每 5 分钟） */
    private String hotPostsCron = "0 */5 * * * ?";

    /** 创作者排行刷新 cron（默认每日 02:00） */
    private String creatorCron = "0 0 2 * * ?";

    /** 话题排行刷新 cron（默认每日 03:00） */
    private String topicCron = "0 0 3 * * ?";

    /** 日榜归档 cron（默认每日 02:00） */
    private String dailyArchiveCron = "0 0 2 * * ?";

    /** 周榜归档 cron（默认每周一 03:00） */
    private String weeklyArchiveCron = "0 0 3 ? * MON";

    /** 月榜归档 cron（默认每月 1 日 04:00） */
    private String monthlyArchiveCron = "0 0 4 1 * ?";
}
