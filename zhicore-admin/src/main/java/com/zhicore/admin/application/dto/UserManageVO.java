package com.zhicore.admin.application.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户管理视图对象
 */
@Data
@Builder
public class UserManageVO {
    
    /**
     * 用户ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    
    /**
     * 用户名
     */
    private String username;
    
    /**
     * 邮箱
     */
    private String email;
    
    /**
     * 昵称
     */
    private String nickname;
    
    /**
     * 头像
     */
    private String avatar;
    
    /**
     * 状态
     */
    private String status;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
    
    /**
     * 角色列表
     */
    private List<String> roles;
}
