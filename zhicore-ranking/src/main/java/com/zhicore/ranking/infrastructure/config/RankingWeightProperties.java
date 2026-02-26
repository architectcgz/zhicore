package com.zhicore.ranking.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 热度计算权重配置
 *
 * <p>使用 @ConfigurationProperties 绑定，支持 Nacos 动态刷新（无需 @RefreshScope）</p>
 *
 * @author ZhiCore Team
 */
@Data
@Component
@ConfigurationProperties(prefix = "ranking.weight")
public class RankingWeightProperties {

    private double view = 1.0;
    private double like = 5.0;
    private double comment = 10.0;
    private double favorite = 8.0;
    private double halfLifeDays = 7.0;
}
