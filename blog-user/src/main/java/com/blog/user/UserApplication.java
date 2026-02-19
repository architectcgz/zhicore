package com.blog.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 用户服务启动类
 *
 * @author Blog Team
 */
@SpringBootApplication(scanBasePackages = {"com.blog.user", "com.blog.common", "com.blog.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.blog.api.client", "com.blog.user.infrastructure.feign"})
@MapperScan("com.blog.user.infrastructure.repository.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
