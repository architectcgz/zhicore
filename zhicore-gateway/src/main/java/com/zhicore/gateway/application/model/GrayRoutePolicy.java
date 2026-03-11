package com.zhicore.gateway.application.model;

import java.util.Set;

/**
 * 灰度路由策略的只读配置。
 */
public record GrayRoutePolicy(
        boolean enabled,
        int percentage,
        Set<String> grayUserIds,
        Set<String> grayServices
) {
}
