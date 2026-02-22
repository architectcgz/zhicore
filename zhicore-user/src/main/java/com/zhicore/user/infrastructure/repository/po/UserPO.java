package com.zhicore.user.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 用户持久化对象
 *
 * @author ZhiCore Team
 */
@Data
@TableName("users")
public class UserPO {

    @TableId(type = IdType.INPUT)
    private Long id;

    @TableField("username")
    private String userName;

    @TableField("nick_name")
    private String nickName;

    private String email;

    @TableField("email_confirmed")
    private Boolean emailConfirmed;

    @TableField("password_hash")
    private String passwordHash;

    @TableField("phone_number")
    private String phoneNumber;

    @TableField("phone_number_confirmed")
    private Boolean phoneNumberConfirmed;

    @TableField("avatar_id")
    private String avatarId;

    private String bio;

    @TableField("profile_version")
    private Long profileVersion;

    @TableField("is_active")
    private Boolean isActive;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;

    @TableField("deleted")
    private Boolean deleted;
}
