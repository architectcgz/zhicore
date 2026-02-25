package com.zhicore.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据迁移服务启动类
 * 负责数据库迁移、CDC配置、灰度发布管理
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class MigrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(MigrationApplication.class, args);
    }
}
