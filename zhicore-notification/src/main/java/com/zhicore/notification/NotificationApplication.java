package com.zhicore.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 通知服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.ZhiCore.notification", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.zhicore.api.client", "com.ZhiCore.notification.infrastructure.feign"})
@MapperScan("com.ZhiCore.notification.infrastructure.repository.mapper")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
