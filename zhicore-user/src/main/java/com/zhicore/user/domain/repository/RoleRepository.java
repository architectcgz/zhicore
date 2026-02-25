package com.zhicore.user.domain.repository;

import com.zhicore.user.domain.model.Role;

import java.util.Optional;
import java.util.Set;

/**
 * 角色仓储接口
 *
 * @author ZhiCore Team
 */
public interface RoleRepository {

    /**
     * 根据ID查询角色
     *
     * @param id 角色ID
     * @return 角色
     */
    Optional<Role> findById(Integer id);

    /**
     * 根据名称查询角色
     *
     * @param name 角色名称
     * @return 角色
     */
    Optional<Role> findByName(String name);

    /**
     * 查询用户的所有角色
     *
     * @param userId 用户ID
     * @return 角色集合
     */
    Set<Role> findByUserId(Long userId);

    /**
     * 保存用户角色关联
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void saveUserRole(Long userId, Integer roleId);

    /**
     * 删除用户角色关联
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void deleteUserRole(Long userId, Integer roleId);
}
