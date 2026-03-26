package com.zhicore.notification;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UserFollowerShardClient;
import com.zhicore.api.client.UserServiceClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 通知服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.zhicore.notification", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        IdGeneratorFeignClient.class,
        UserFollowerShardClient.class,
        UserServiceClient.class
})
@MapperScan("com.zhicore.notification.infrastructure.repository.mapper")
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}
