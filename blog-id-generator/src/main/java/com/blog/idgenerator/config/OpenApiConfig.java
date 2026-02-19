package com.blog.idgenerator.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Knife4j 配置类
 * 
 * 配置API文档的基本信息和展示方式
 */
@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Blog ID Generator Service API")
                        .version("1.0.0")
                        .description("博客系统分布式ID生成服务，提供Snowflake和Segment两种模式的ID生成")
                        .contact(new Contact()
                                .name("Blog Platform Team")
                                .email("support@blog-platform.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")));
    }
}
