package com.zhicore.api.dto.user;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户 DTO
 */
@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String userName;
    private String nickName;
    private String email;
    private String avatarUrl;
    private String bio;
    private Integer status;
    private LocalDateTime createdAt;

    // 统计信息
    private Integer followingCount;
    private Integer followersCount;
    private Integer postCount;
}
