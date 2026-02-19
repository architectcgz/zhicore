package com.blog.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 搜索服务启动类
 *
 * @author Blog Team
 */
@SpringBootApplication(scanBasePackages = {"com.blog.search", "com.blog.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = {"com.blog.search.infrastructure.feign", "com.blog.api.client"})
public class SearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchApplication.class, args);
    }
}
