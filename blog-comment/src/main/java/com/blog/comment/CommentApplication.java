package com.blog.comment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 评论服务启动类
 *
 * @author Blog Team
 */
@SpringBootApplication(scanBasePackages = {"com.blog.comment", "com.blog.common", "com.blog.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.blog.api.client", "com.blog.comment.infrastructure.feign"})
@MapperScan("com.blog.comment.infrastructure.repository.mapper")
public class CommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentApplication.class, args);
    }
}
