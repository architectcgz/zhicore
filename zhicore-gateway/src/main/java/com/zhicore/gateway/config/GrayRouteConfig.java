package com.zhicore.gateway.config;

import com.zhicore.gateway.application.model.GrayRoutePolicy;
import com.zhicore.gateway.application.service.GrayRouteService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * 灰度路由应用层装配。
 */
@Configuration
public class GrayRouteConfig {

    @Bean
    public GrayRouteService grayRouteService(GrayReleaseProperties grayReleaseProperties) {
        GrayRoutePolicy policy = new GrayRoutePolicy(
                grayReleaseProperties.isEnabled(),
                grayReleaseProperties.getPercentage(),
                Set.copyOf(grayReleaseProperties.getGrayUserIds()),
                Set.copyOf(grayReleaseProperties.getGrayServices())
        );
        return new GrayRouteService(policy);
    }
}
