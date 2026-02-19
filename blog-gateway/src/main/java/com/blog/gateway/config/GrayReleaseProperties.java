package com.blog.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 灰度发布配置属性
 *
 * @author Blog Team
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "gray")
public class GrayReleaseProperties {

    /**
     * 是否启用灰度发布
     */
    private boolean enabled = false;

    /**
     * 灰度流量百分比（0-100）
     */
    private int percentage = 0;

    /**
     * 灰度用户ID列表
     */
    private Set<String> grayUserIds = new HashSet<>();

    /**
     * 灰度服务列表
     */
    private Set<String> grayServices = new HashSet<>();
}
