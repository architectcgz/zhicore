package com.zhicore.content.infrastructure.cache;

import com.zhicore.content.application.port.cache.CacheRepository;
import com.zhicore.content.infrastructure.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class RedisCacheRepositoryImplIntegrationTest extends IntegrationTestBase {

    @Autowired
    private CacheRepository cacheRepository;

    @Test
    void shouldLoadCacheRepositoryBean() {
        assertThat(cacheRepository).isNotNull();
    }
}
