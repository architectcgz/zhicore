package com.zhicore.comment;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.comment.infrastructure.feign.PostServiceClient;
import com.zhicore.comment.infrastructure.feign.UserServiceClient;
import com.zhicore.comment.infrastructure.feign.ZhiCoreUploadClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 评论服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.zhicore.comment", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableScheduling
@EnableFeignClients(clients = {
        IdGeneratorFeignClient.class,
        PostServiceClient.class,
        UserServiceClient.class,
        ZhiCoreUploadClient.class
})
@MapperScan("com.zhicore.comment.infrastructure.repository.mapper")
public class CommentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommentApplication.class, args);
    }
}
