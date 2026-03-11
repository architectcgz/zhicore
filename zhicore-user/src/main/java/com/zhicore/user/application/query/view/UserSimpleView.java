package com.zhicore.user.application.query.view;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户简要信息查询视图。
 *
 * 仅供 user 服务内部 query 链路使用，避免 application 直接暴露跨服务 DTO。
 */
@Data
public class UserSimpleView implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String userName;
    private String nickname;
    private String avatarId;
    private Long profileVersion;
}
