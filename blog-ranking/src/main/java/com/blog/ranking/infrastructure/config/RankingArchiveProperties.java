package com.blog.ranking.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 排行榜归档配置属性
 * 
 * 使用 @ConfigurationProperties 自动绑定配置，支持动态刷新
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ranking.archive")
public class RankingArchiveProperties {
    
    /**
     * 归档查询限制
     * 默认 100
     */
    @Min(value = 10, message = "归档查询限制不能少于 10")
    private int limit = 100;
}
