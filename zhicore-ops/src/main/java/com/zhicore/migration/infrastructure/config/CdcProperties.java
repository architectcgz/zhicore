package com.zhicore.migration.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CDC 配置属性
 */
@Data
@Component
@ConfigurationProperties(prefix = "cdc")
public class CdcProperties {

    /**
     * 是否启用 CDC
     */
    private boolean enabled = false;

    /**
     * 连接器配置
     */
    private Connector connector = new Connector();

    @Data
    public static class Connector {
        /**
         * 连接器名称
         */
        private String name = "ZhiCore-postgres-connector";

        /**
         * 数据库配置
         */
        private Database database = new Database();

        /**
         * 复制槽配置
         */
        private Slot slot = new Slot();

        /**
         * 发布配置
         */
        private Publication publication = new Publication();

        /**
         * 监听的表列表
         */
        private List<String> tables;
    }

    @Data
    public static class Database {
        private String hostname = "localhost";
        private int port = 5432;
        private String user = "postgres";
        private String password = "postgres";
        private String dbname = "ZhiCore";
    }

    @Data
    public static class Slot {
        private String name = "ZhiCore_cdc_slot";
    }

    @Data
    public static class Publication {
        private String name = "ZhiCore_publication";
    }
}
