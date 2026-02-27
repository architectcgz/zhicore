package com.zhicore.common.cache;

import com.zhicore.common.config.CacheProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redis Key 全局命名空间初始化器
 *
 * 在 Spring 启动时从 CacheProperties 读取命名空间配置，
 * 设置到 CacheConstants 的静态字段中，供各模块 RedisKeys 工具类使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheNamespaceInitializer {

    private final CacheProperties cacheProperties;

    @PostConstruct
    public void init() {
        String namespace = cacheProperties.getKeyNamespace();
        if (namespace != null && !namespace.isBlank()) {
            CacheConstants.setGlobalNamespace(namespace);
            log.info("Redis key namespace initialized: {}", namespace);
        }
    }
}
