package com.zhicore.comment;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 评论服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.ZhiCore.comment", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.zhicore.api.client", "com.ZhiCore.comment.infrastructure.feign"})
@MapperScan("com.ZhiCore.comment.infrastructure.repository.mapper")
public class CommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentApplication.class, args);
    }
}
