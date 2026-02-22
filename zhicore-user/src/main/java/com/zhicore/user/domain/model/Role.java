package com.zhicore.user.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import org.springframework.util.Assert;

import java.util.Objects;

/**
 * 角色实体
 *
 * @author ZhiCore Team
 */
@Getter
public class Role {

    /**
     * 角色ID
     */
    private final Integer id;

    /**
     * 角色名称
     */
    private final String name;

    /**
     * 角色描述
     */
    private final String description;

    /**
     * 预定义角色常量
     */
    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MODERATOR = "MODERATOR";

    @JsonCreator
    public Role(
            @JsonProperty("id") Integer id, 
            @JsonProperty("name") String name, 
            @JsonProperty("description") String description) {
        Assert.notNull(id, "角色ID不能为空");
        Assert.hasText(name, "角色名称不能为空");
        this.id = id;
        this.name = name;
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Role role = (Role) o;
        return Objects.equals(id, role.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
