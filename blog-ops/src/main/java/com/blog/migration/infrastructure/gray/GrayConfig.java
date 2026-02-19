package com.blog.migration.infrastructure.gray;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Set;

/**
 * 灰度配置
 */
@Data
@Builder
public class GrayConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 是否启用灰度
     */
    private boolean enabled;

    /**
     * 灰度流量比例 (0-100)
     */
    private int trafficRatio;

    /**
     * 白名单用户
     */
    private Set<String> whitelistUsers;

    /**
     * 黑名单用户
     */
    private Set<String> blacklistUsers;
}
