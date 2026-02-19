package com.blog.migration.infrastructure.cdc;

import com.blog.migration.infrastructure.config.CdcProperties;
import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.engine.DebeziumEngine;
import io.debezium.engine.RecordChangeEvent;
import io.debezium.engine.format.ChangeEventFormat;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Debezium CDC 配置
 * 配置 PostgreSQL 逻辑复制连接器
 */
@Slf4j
@org.springframework.context.annotation.Configuration
@ConditionalOnProperty(name = "cdc.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DebeziumConfig {

    private final CdcProperties cdcProperties;
    private final CdcEventHandler cdcEventHandler;

    /**
     * 创建 Debezium 引擎
     */
    @Bean
    public DebeziumEngine<RecordChangeEvent<SourceRecord>> debeziumEngine() {
        Configuration config = Configuration.create()
                // 连接器配置
                .with("name", cdcProperties.getConnector().getName())
                .with("connector.class", "io.debezium.connector.postgresql.PostgresConnector")
                .with("offset.storage", "org.apache.kafka.connect.storage.FileOffsetBackingStore")
                .with("offset.storage.file.filename", "/tmp/offsets.dat")
                .with("offset.flush.interval.ms", "60000")
                
                // 数据库连接配置
                .with("database.hostname", cdcProperties.getConnector().getDatabase().getHostname())
                .with("database.port", cdcProperties.getConnector().getDatabase().getPort())
                .with("database.user", cdcProperties.getConnector().getDatabase().getUser())
                .with("database.password", cdcProperties.getConnector().getDatabase().getPassword())
                .with("database.dbname", cdcProperties.getConnector().getDatabase().getDbname())
                .with("database.server.name", "blog-db")
                
                // 复制槽配置
                .with("slot.name", cdcProperties.getConnector().getSlot().getName())
                .with("publication.name", cdcProperties.getConnector().getPublication().getName())
                .with("plugin.name", "pgoutput")
                
                // 表过滤配置
                .with("table.include.list", String.join(",", 
                        cdcProperties.getConnector().getTables().stream()
                                .map(t -> "public." + t)
                                .toList()))
                
                // 快照配置
                .with("snapshot.mode", "initial")
                
                // 其他配置
                .with("topic.prefix", "blog")
                .with("decimal.handling.mode", "string")
                .with("time.precision.mode", "connect")
                .build();

        DebeziumEngine<RecordChangeEvent<SourceRecord>> engine = DebeziumEngine.create(ChangeEventFormat.of(Connect.class))
                .using(config.asProperties())
                .notifying(record -> {
                    try {
                        cdcEventHandler.handleChangeEvent(record);
                    } catch (Exception e) {
                        log.error("处理 CDC 事件失败", e);
                    }
                })
                .build();

        // 在单独的线程中运行引擎
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(engine);

        // 注册关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                engine.close();
                executor.shutdown();
            } catch (IOException e) {
                log.error("关闭 Debezium 引擎失败", e);
            }
        }));

        return engine;
    }
}
