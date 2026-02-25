package com.zhicore.common.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置属性
 * 
 * 使用 @ConfigurationProperties 自动绑定配置，支持动态刷新
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    
    /**
     * JWT 密钥
     * 用于签名和验证 JWT token
     */
    @NotBlank(message = "JWT 密钥不能为空")
    private String secret;
    
    /**
     * Access Token 过期时间（秒）
     * 默认 2 小时
     */
    @Min(value = 60, message = "Access Token 过期时间不能少于 60 秒")
    private Long accessTokenExpiration = 7200L;
    
    /**
     * Refresh Token 过期时间（秒）
     * 默认 7 天
     */
    @Min(value = 3600, message = "Refresh Token 过期时间不能少于 3600 秒")
    private Long refreshTokenExpiration = 604800L;
}
