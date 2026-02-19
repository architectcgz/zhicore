package com.blog.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 缓存配置属性
 * 支持 Nacos 配置热更新
 *
 * @author Blog Team
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    /**
     * TTL 配置
     */
    private Ttl ttl = new Ttl();

    /**
     * 分布式锁配置
     */
    private Lock lock = new Lock();

    /**
     * 热点数据识别配置
     */
    private HotData hotData = new HotData();

    /**
     * 抖动配置
     */
    private Jitter jitter = new Jitter();

    /**
     * 草稿缓存配置
     */
    private Draft draft = new Draft();

    /**
     * TTL 配置类
     */
    @Data
    public static class Ttl {
        /**
         * 实体详情缓存 TTL（秒）
         * 默认：600秒（10分钟）
         */
        private long entityDetail = 600;

        /**
         * 列表缓存 TTL（秒）
         */
        private long list = 300;

        /**
         * 统计数据缓存 TTL（秒），-1 表示永久
         */
        private long stats = -1;

        /**
         * 会话缓存 TTL（秒）
         */
        private long session = 604800;

        /**
         * 空值缓存 TTL（秒）
         * 默认：60秒
         */
        private long nullValue = 60;
    }

    /**
     * 分布式锁配置类
     */
    @Data
    public static class Lock {
        /**
         * 分布式锁等待时间（秒）
         * 默认：5秒
         */
        private long waitTime = 5;

        /**
         * 分布式锁持有时间（秒）
         * 默认：10秒
         */
        private long leaseTime = 10;

        /**
         * 是否使用公平锁
         * 默认：false（非公平锁）
         */
        private boolean fair = false;
    }

    /**
     * 热点数据识别配置类
     */
    @Data
    public static class HotData {
        /**
         * 是否启用热点数据识别
         * 默认：true
         */
        private boolean enabled = true;

        /**
         * 热点数据阈值（访问次数）
         * 默认：100次/小时
         */
        private int threshold = 100;
    }

    /**
     * 抖动配置类
     * 用于防止缓存雪崩，在基础 TTL 上添加随机偏移
     */
    @Data
    public static class Jitter {
        /**
         * 最大抖动时间（秒）
         * 默认：60秒
         */
        private int maxSeconds = 60;
    }

    /**
     * 草稿缓存配置类
     */
    @Data
    public static class Draft {
        /**
         * 单个草稿缓存 TTL（秒）
         * 
         * 草稿频繁更新，使用较短的 TTL
         * 默认值：300 秒（5 分钟）
         * 取值范围：60-3600 秒
         */
        private long ttl = 300;

        /**
         * 草稿列表缓存 TTL（秒）
         * 
         * 列表数据更新频率较高，使用更短的 TTL
         * 默认值：180 秒（3 分钟）
         * 取值范围：60-1800 秒
         */
        private long listTtl = 180;
    }
}
