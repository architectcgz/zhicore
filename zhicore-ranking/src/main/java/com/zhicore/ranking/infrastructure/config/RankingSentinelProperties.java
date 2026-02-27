package com.zhicore.ranking.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 排行榜 Sentinel 限流配置
 *
 * <p>支持 Nacos 热更新（@RefreshScope），配置变更后 Bean 重建，规则自动重新加载。</p>
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "ranking.sentinel")
public class RankingSentinelProperties {

    /** 热榜查询 QPS 限制 */
    private int hotPostsQps = 1000;

    /** 创作者排行 QPS 限制 */
    private int creatorQps = 500;

    /** 话题排行 QPS 限制 */
    private int topicQps = 500;
}
