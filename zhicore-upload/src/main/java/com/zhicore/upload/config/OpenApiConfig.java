package com.zhicore.upload.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置
 * 
 * 配置 Knife4j API 文档
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("文件上传服务 API")
                        .version("1.0.0")
                        .description("文件上传服务微服务接口文档，作为 file-service 的轻量级代理层，为其他微服务提供统一的文件上传接口")
                        .contact(new Contact()
                                .name("ZhiCore Team")
                                .email("support@ZhiCore.com")));
    }
}
