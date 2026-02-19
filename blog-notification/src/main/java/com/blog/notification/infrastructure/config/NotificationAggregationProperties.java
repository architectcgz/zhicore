package com.blog.notification.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 通知聚合配置属性类
 * 
 * 用于外部化通知聚合服务的配置参数，支持通过配置文件或 Nacos 动态调整。
 * 配置前缀：blog.notification.aggregation
 * 
 * @author Blog Team
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "blog.notification.aggregation")
public class NotificationAggregationProperties {

    /**
     * 缓存配置
     */
    private CacheConfig cache = new CacheConfig();

    /**
     * 显示配置
     */
    private DisplayConfig display = new DisplayConfig();

    /**
     * 缓存配置类
     */
    @Data
    public static class CacheConfig {
        /**
         * 缓存 TTL（生存时间），单位：秒
         * 默认值：300 秒（5 分钟）
         * 有效范围：1-3600 秒（1 秒到 1 小时）
         * 
         * 控制聚合通知结果在 Redis 中的缓存时长。
         * 
         * 权衡考虑：
         * - 较短的 TTL：数据更新更及时，但会增加数据库查询负载
         * - 较长的 TTL：减少数据库查询，但可能导致用户看到过时的通知数据
         * 
         * 建议值：
         * - 高并发场景：300-600 秒（5-10 分钟）
         * - 实时性要求高：60-180 秒（1-3 分钟）
         * - 低并发场景：600-1800 秒（10-30 分钟）
         */
        @Min(value = 1, message = "缓存 TTL 必须至少为 1 秒")
        @Max(value = 3600, message = "缓存 TTL 不能超过 1 小时（3600 秒）")
        private long ttl = 300;
    }

    /**
     * 显示配置类
     */
    @Data
    public static class DisplayConfig {
        /**
         * 聚合通知中显示的最大最近触发者数量
         * 默认值：3
         * 有效范围：1-10
         * 
         * 控制在聚合通知文案中显示多少个用户名。
         * 例如："张三等5人赞了你的文章" 中最多显示 3 个用户名。
         * 
         * 权衡考虑：
         * - 较大的值：提供更详细的信息，但文案会更长
         * - 较小的值：文案更简洁，但信息量较少
         * 
         * 建议值：
         * - 移动端：2-3（屏幕空间有限）
         * - PC 端：3-5（有更多显示空间）
         * - 简洁风格：1-2
         */
        @Min(value = 1, message = "最大最近触发者数量必须至少为 1")
        @Max(value = 10, message = "最大最近触发者数量不能超过 10")
        private int maxRecentActors = 3;
    }
}
