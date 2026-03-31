package com.zhicore.api.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理侧用户视图 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserManageDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String email;
    private String nickname;
    private String avatar;
    private String status;
    private OffsetDateTime createdAt;
    private List<String> roles;
}
