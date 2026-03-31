package com.zhicore.user.application.query.view;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 管理侧用户查询视图。
 *
 * 仅供 user 服务内部 query 链路使用，避免 application 直接暴露跨服务 DTO。
 */
@Data
@Builder
public class UserManageView implements Serializable {

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
