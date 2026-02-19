package com.blog.idgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * ID生成服务启动类
 * 
 * 作为 id-generator-server 的轻量级代理层，为其他微服务提供统一的分布式ID生成接口
 */
@SpringBootApplication
@EnableDiscoveryClient
public class IdGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(IdGeneratorApplication.class, args);
    }
}
