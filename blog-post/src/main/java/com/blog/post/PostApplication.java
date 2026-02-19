package com.blog.post;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * 文章服务启动类
 *
 * @author Blog Team
 */
@SpringBootApplication(scanBasePackages = {"com.blog.post", "com.blog.common", "com.blog.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.blog.api.client", "com.blog.post.infrastructure.feign"})
@EnableMongoRepositories(basePackages = "com.blog.post.infrastructure.mongodb.repository")
public class PostApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostApplication.class, args);
    }
}
