package com.blog.notification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 通知服务启动类
 *
 * @author Blog Team
 */
@SpringBootApplication(scanBasePackages = {"com.blog.notification", "com.blog.common", "com.blog.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.blog.api.client", "com.blog.notification.infrastructure.feign"})
@MapperScan("com.blog.notification.infrastructure.repository.mapper")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
