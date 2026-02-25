package com.zhicore.message;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 消息服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.ZhiCore.message", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.zhicore.api.client", "com.ZhiCore.message.infrastructure.feign"})
@MapperScan("com.ZhiCore.message.infrastructure.repository.mapper")
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
