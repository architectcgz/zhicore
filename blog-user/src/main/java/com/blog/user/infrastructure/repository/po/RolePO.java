package com.blog.user.infrastructure.repository.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色持久化对象
 *
 * @author Blog Team
 */
@Data
@TableName("roles")
public class RolePO {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String name;

    private String description;
}
