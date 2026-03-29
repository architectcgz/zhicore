package com.zhicore.user;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.PostServiceClient;
import com.zhicore.user.infrastructure.feign.ZhiCoreUploadClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 用户服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.zhicore.user", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        IdGeneratorFeignClient.class,
        PostServiceClient.class,
        ZhiCoreUploadClient.class
})
@EnableScheduling
@MapperScan("com.zhicore.user.infrastructure.repository.mapper")
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
    }
}
