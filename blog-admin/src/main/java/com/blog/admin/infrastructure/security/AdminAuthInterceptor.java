package com.blog.admin.infrastructure.security;

import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员权限拦截器
 * 验证请求头中的 X-User-Roles 是否包含 ADMIN 角色
 *
 * @author Blog Team
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String ROLE_HEADER = "X-User-Roles";
    private static final String ADMIN_ROLE = "ADMIN";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 只处理带有 @RequireAdmin 注解的方法
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;
        
        // 检查方法或类上是否有 @RequireAdmin 注解
        RequireAdmin methodAnnotation = handlerMethod.getMethodAnnotation(RequireAdmin.class);
        RequireAdmin classAnnotation = handlerMethod.getBeanType().getAnnotation(RequireAdmin.class);
        
        if (methodAnnotation == null && classAnnotation == null) {
            return true;
        }

        // 验证管理员角色
        String roles = request.getHeader(ROLE_HEADER);
        if (roles == null || !roles.contains(ADMIN_ROLE)) {
            log.warn("Access denied: user does not have ADMIN role. Roles: {}", roles);
            throw new BusinessException(ResultCode.FORBIDDEN, "需要管理员权限");
        }

        return true;
    }
}
