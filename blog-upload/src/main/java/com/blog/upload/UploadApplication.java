package com.blog.upload;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文件上传服务启动类
 * 
 * 作为 file-service 的轻量级代理层，为其他微服务提供统一的文件上传接口
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class UploadApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadApplication.class, args);
    }
}
