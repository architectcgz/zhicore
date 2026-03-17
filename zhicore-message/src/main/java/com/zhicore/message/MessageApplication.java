package com.zhicore.message;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.message.infrastructure.feign.ImExternalMessageClient;
import com.zhicore.message.infrastructure.feign.ImInternalMessageClient;
import com.zhicore.message.infrastructure.feign.ImOpenIdClient;
import com.zhicore.message.infrastructure.feign.UserServiceClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 消息服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.zhicore.message", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        IdGeneratorFeignClient.class,
        UserServiceClient.class,
        ImOpenIdClient.class,
        ImExternalMessageClient.class,
        ImInternalMessageClient.class
})
@MapperScan("com.zhicore.message.infrastructure.repository.mapper")
@EnableScheduling
public class MessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(MessageApplication.class, args);
    }
}
