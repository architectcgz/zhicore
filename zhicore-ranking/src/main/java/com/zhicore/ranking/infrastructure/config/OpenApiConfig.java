package com.zhicore.ranking.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置类
 * 配置 Knife4j API 文档
 *
 * @author ZhiCore Team
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI(
            @Value("${spring.application.name}") String applicationName,
            @Value("${knife4j.title:API 文档}") String title,
            @Value("${knife4j.version:1.0}") String version,
            @Value("${knife4j.description:}") String description
    ) {
        return new OpenAPI()
                .info(new Info()
                        .title(title)
                        .version(version)
                        .description(description)
                        .contact(new Contact()
                                .name("博客系统开发团队")
                                .email("dev@ZhiCore.com")))
                .components(new Components()
                        .addSecuritySchemes("bearer-jwt",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("请输入 JWT Token")));
    }

    @Bean
    public GroupedOpenApi defaultApi() {
        return GroupedOpenApi.builder()
                .group("default")
                .pathsToMatch("/**")
                .packagesToScan("com.ZhiCore.ranking")
                .build();
    }
    
    @Bean
    public GroupedOpenApi publicApi() {
        return GroupedOpenApi.builder()
                .group("全部接口")
                .pathsToMatch("/**")
                .packagesToScan("com.ZhiCore.ranking")
                .build();
    }

    @Bean
    public GroupedOpenApi postRankingApi() {
        return GroupedOpenApi.builder()
                .group("文章排行榜")
                .pathsToMatch("/api/v1/ranking/posts/**")
                .packagesToScan("com.ZhiCore.ranking")
                .build();
    }

    @Bean
    public GroupedOpenApi creatorRankingApi() {
        return GroupedOpenApi.builder()
                .group("创作者排行榜")
                .pathsToMatch("/api/v1/ranking/creators/**")
                .packagesToScan("com.ZhiCore.ranking")
                .build();
    }

    @Bean
    public GroupedOpenApi topicRankingApi() {
        return GroupedOpenApi.builder()
                .group("话题排行榜")
                .pathsToMatch("/api/v1/ranking/topics/**")
                .packagesToScan("com.ZhiCore.ranking")
                .build();
    }
}
