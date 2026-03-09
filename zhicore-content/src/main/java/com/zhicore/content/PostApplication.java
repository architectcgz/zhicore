package com.zhicore.content;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.content.infrastructure.feign.ContentUploadServiceClient;
import com.zhicore.content.infrastructure.feign.ContentUserServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 文章服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.zhicore.content", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        IdGeneratorFeignClient.class,
        ContentUserServiceClient.class,
        ContentUploadServiceClient.class
})
@EnableMongoRepositories(basePackages = "com.zhicore.content.infrastructure.persistence.mongo.repository")
@EnableScheduling
@EnableAsync
public class PostApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostApplication.class, args);
    }
}

