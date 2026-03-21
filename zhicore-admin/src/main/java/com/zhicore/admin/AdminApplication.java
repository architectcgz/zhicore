package com.zhicore.admin;

import com.zhicore.admin.infrastructure.feign.AdminCommentServiceClient;
import com.zhicore.admin.infrastructure.feign.AdminPostServiceClient;
import com.zhicore.admin.infrastructure.feign.AdminUserServiceClient;
import com.zhicore.admin.infrastructure.feign.IdGeneratorClient;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 管理服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.zhicore.admin", "com.zhicore.common", "com.zhicore.api"})
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        AdminUserServiceClient.class,
        AdminPostServiceClient.class,
        AdminCommentServiceClient.class,
        IdGeneratorClient.class
})
@MapperScan("com.zhicore.admin.infrastructure.repository.mapper")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
