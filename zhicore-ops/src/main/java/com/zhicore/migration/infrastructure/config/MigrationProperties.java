package com.zhicore.migration.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 数据迁移配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "migration")
public class MigrationProperties {

    /**
     * 源数据库配置
     */
    private SourceDatabase source = new SourceDatabase();

    /**
     * 批量处理大小
     */
    private int batchSize = 1000;

    /**
     * 并行线程数
     */
    private int parallelThreads = 4;

    /**
     * 是否启用数据校验
     */
    private boolean validationEnabled = true;

    @Data
    public static class SourceDatabase {
        private String url;
        private String username;
        private String password;
    }
}
