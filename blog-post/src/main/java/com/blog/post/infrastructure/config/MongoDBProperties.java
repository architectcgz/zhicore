package com.blog.post.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * MongoDB 配置属性
 * 
 * 使用 @ConfigurationProperties 自动绑定配置，支持动态刷新
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "spring.data.mongodb")
public class MongoDBProperties {
    
    /**
     * MongoDB 主机地址
     */
    @NotBlank(message = "MongoDB 主机地址不能为空")
    private String host = "localhost";
    
    /**
     * MongoDB 端口
     */
    @Min(value = 1, message = "端口号必须大于 0")
    @Max(value = 65535, message = "端口号不能超过 65535")
    private int port = 27017;
    
    /**
     * 数据库名称
     */
    @NotBlank(message = "数据库名称不能为空")
    private String database = "blog";
    
    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username = "admin";
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password = "mongo123456";
    
    /**
     * 认证数据库
     */
    @NotBlank(message = "认证数据库不能为空")
    private String authenticationDatabase = "admin";
    
    /**
     * 连接超时时间（毫秒）
     */
    @Min(value = 1000, message = "连接超时时间不能少于 1000 毫秒")
    private int connectionTimeout = 10000;
    
    /**
     * Socket 超时时间（毫秒）
     */
    @Min(value = 1000, message = "Socket 超时时间不能少于 1000 毫秒")
    private int socketTimeout = 10000;
    
    /**
     * 最大连接池大小
     */
    @Min(value = 10, message = "最大连接池大小不能少于 10")
    @Max(value = 1000, message = "最大连接池大小不能超过 1000")
    private int maxConnectionPoolSize = 100;
    
    /**
     * 最小连接池大小
     */
    @Min(value = 1, message = "最小连接池大小不能少于 1")
    private int minConnectionPoolSize = 10;
}
