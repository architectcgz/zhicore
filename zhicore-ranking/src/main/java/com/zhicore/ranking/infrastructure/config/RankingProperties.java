package com.zhicore.ranking.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 排行榜配置属性
 * 
 * 配置排行榜接口的分页参数，包括默认页面大小和最大页面大小限制
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "ZhiCore.ranking.pagination")
public class RankingProperties {
    
    /**
     * 默认分页大小
     * 
     * 当客户端未指定页面大小时使用此值
     * 默认值：20
     * 取值范围：1-100
     */
    @Min(value = 1, message = "默认分页大小必须至少为 1")
    @Max(value = 100, message = "默认分页大小不能超过 100")
    private int defaultSize = 20;
    
    /**
     * 最大分页大小
     * 
     * 限制客户端可以请求的最大页面大小，防止单次查询数据量过大
     * 默认值：100
     * 取值范围：1-1000
     */
    @Min(value = 1, message = "最大分页大小必须至少为 1")
    @Max(value = 1000, message = "最大分页大小不能超过 1000")
    private int maxSize = 100;
}
