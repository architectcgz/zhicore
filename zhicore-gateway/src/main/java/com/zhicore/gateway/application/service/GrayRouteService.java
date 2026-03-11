package com.zhicore.gateway.application.service;

import com.zhicore.gateway.application.model.GrayRoutePolicy;

/**
 * 灰度路由决策服务。
 */
public class GrayRouteService {

    private final GrayRoutePolicy policy;

    public GrayRouteService(GrayRoutePolicy policy) {
        this.policy = policy;
    }

    /**
     * 根据灰度策略判断请求是否应进入灰度流量。
     */
    public boolean shouldRouteToGray(String userId, String clientAddress, String fallbackIdentifier) {
        if (!policy.enabled()) {
            return false;
        }

        if (userId != null && policy.grayUserIds().contains(userId)) {
            return true;
        }

        if (policy.percentage() <= 0) {
            return false;
        }

        String identifier = userId != null ? userId
                : clientAddress != null ? clientAddress
                : fallbackIdentifier;
        if (identifier == null) {
            return false;
        }

        int hash = Math.abs(identifier.hashCode());
        return (hash % 100) < policy.percentage();
    }
}
