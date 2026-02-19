package com.blog.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 消息服务启动类
 *
 * @author Blog Team
 */
@SpringBootApplication(scanBasePackages = {"com.blog.message", "com.blog.common", "com.blog.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.blog.api.client", "com.blog.message.infrastructure.feign"})
@MapperScan("com.blog.message.infrastructure.repository.mapper")
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
