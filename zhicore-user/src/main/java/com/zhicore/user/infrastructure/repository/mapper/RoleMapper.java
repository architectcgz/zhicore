package com.zhicore.user.infrastructure.repository.mapper;

import com.zhicore.user.infrastructure.repository.po.RolePO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.*;

import java.util.Set;

/**
 * 角色 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface RoleMapper extends BaseMapper<RolePO> {

    /**
     * 根据名称查询角色
     */
    @Select("SELECT * FROM roles WHERE name = #{name}")
    RolePO selectByName(@Param("name") String name);

    /**
     * 查询用户的所有角色
     */
    @Select("SELECT r.* FROM roles r " +
            "INNER JOIN user_roles ur ON r.id = ur.role_id " +
            "WHERE ur.user_id = #{userId}")
    Set<RolePO> selectByUserId(@Param("userId") Long userId);

    /**
     * 保存用户角色关联
     */
    @Insert("INSERT INTO user_roles (user_id, role_id) VALUES (#{userId}, #{roleId})")
    void insertUserRole(@Param("userId") Long userId, @Param("roleId") Integer roleId);

    /**
     * 删除用户角色关联
     */
    @Delete("DELETE FROM user_roles WHERE user_id = #{userId} AND role_id = #{roleId}")
    void deleteUserRole(@Param("userId") Long userId, @Param("roleId") Integer roleId);
}
