package com.blog.admin.infrastructure.security;

import java.lang.annotation.*;

/**
 * 需要管理员权限注解
 * 标记在需要管理员权限的控制器或方法上
 *
 * @author Blog Team
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequireAdmin {
}
