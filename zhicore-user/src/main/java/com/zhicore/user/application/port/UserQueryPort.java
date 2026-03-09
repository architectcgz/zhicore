package com.zhicore.user.application.port;

import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.user.application.dto.UserVO;

import java.util.Map;
import java.util.Set;

/**
 * 用户查询端口
 *
 * 定义用户查询的契约，由 UserApplicationService 实现数据源查询，
 * 由 CacheAsideUserQuery 实现缓存装饰。
 * Controller 层注入此接口，Spring 通过 @Primary 自动路由到缓存装饰器。
 */
public interface UserQueryPort {

    /**
     * 根据ID获取用户详情
     *
     * @param userId 用户ID
     * @return 用户视图对象
     */
    UserVO getUserById(Long userId);

    /**
     * 根据ID获取用户简要信息
     *
     * @param userId 用户ID
     * @return 用户简要信息DTO
     */
    UserSimpleDTO getUserSimpleById(Long userId);

    /**
     * 批量获取用户简要信息
     *
     * @param userIds 用户ID集合
     * @return 用户ID到简要信息的映射
     */
    Map<Long, UserSimpleDTO> batchGetUsersSimple(Set<Long> userIds);

    /**
     * 获取用户是否允许陌生人消息。
     *
     * @param userId 用户ID
     * @return 是否允许陌生人消息
     */
    boolean isStrangerMessageAllowed(Long userId);
}
