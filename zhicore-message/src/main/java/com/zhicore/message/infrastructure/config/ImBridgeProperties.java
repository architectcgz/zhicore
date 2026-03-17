package com.zhicore.message.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IM bridge 配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "message.im-bridge")
public class ImBridgeProperties {

    /**
     * 是否启用与 im-system 的桥接。
     */
    private boolean enabled = false;

    /**
     * im-system appId。
     */
    private Integer appId = 1;

    /**
     * OpenId provider。
     */
    private String provider = "blog";

    /**
     * 本地消息映射缓存天数。
     */
    private long mappingTtlDays = 30;

    /**
     * im-system 内部接口 shared token。
     */
    private String internalToken = "";
}
