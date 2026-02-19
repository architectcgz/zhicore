package com.blog.gateway.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * JWT Token 验证结果
 * 
 * @author Blog Team
 */
@Data
@Builder
public class ValidationResult implements Serializable {
    private static final long serialVersionUID = 1L;
    
    /**
     * 用户 ID
     */
    private String userId;
    
    /**
     * 用户名
     */
    private String userName;
    
    /**
     * 用户角色（逗号分隔）
     */
    private String roles;
    
    /**
     * 用于 Jackson 反序列化的构造函数
     */
    @JsonCreator
    public ValidationResult(
            @JsonProperty("userId") String userId,
            @JsonProperty("userName") String userName,
            @JsonProperty("roles") String roles) {
        this.userId = userId;
        this.userName = userName;
        this.roles = roles;
    }
}
