package com.zhicore.ranking.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 排行榜 Sentinel 限流配置
 *
 * <p>支持通过 Nacos 覆盖默认值，修改后需重启生效。
 * 如需运行时热更新，后续可改为监听 RefreshScopeRefreshedEvent 重新加载规则。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "ranking.sentinel")
public class RankingSentinelProperties {

    /** 热榜查询 QPS 限制 */
    private int hotPostsQps = 1000;

    /** 创作者排行 QPS 限制 */
    private int creatorQps = 500;

    /** 话题排行 QPS 限制 */
    private int topicQps = 500;

    /** Warm Up 预热时间（秒），服务启动后 QPS 从 count/3 线性爬升到 count */
    private int warmUpPeriodSec = 30;
}
