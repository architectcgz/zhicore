package com.zhicore.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 搜索服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.ZhiCore.search", "com.zhicore.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.ZhiCore.search.infrastructure.feign", "com.zhicore.api.client"})
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
