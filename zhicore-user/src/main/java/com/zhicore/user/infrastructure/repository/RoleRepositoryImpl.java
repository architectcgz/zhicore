package com.zhicore.user.infrastructure.repository;

import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.repository.RoleRepository;
import com.zhicore.user.infrastructure.repository.mapper.RoleMapper;
import com.zhicore.user.infrastructure.repository.po.RolePO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色仓储实现
 *
 * @author ZhiCore Team
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final RoleMapper roleMapper;

    @Override
    public Optional<Role> findById(Integer id) {
        RolePO po = roleMapper.selectById(id);
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public Optional<Role> findByName(String name) {
        RolePO po = roleMapper.selectByName(name);
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public Set<Role> findByUserId(Long userId) {
        Set<RolePO> poSet = roleMapper.selectByUserId(userId);
        return poSet.stream()
                .map(this::toDomain)
                .collect(Collectors.toSet());
    }

    @Override
    public void saveUserRole(Long userId, Integer roleId) {
        roleMapper.insertUserRole(userId, roleId);
    }

    @Override
    public void deleteUserRole(Long userId, Integer roleId) {
        roleMapper.deleteUserRole(userId, roleId);
    }

    private Role toDomain(RolePO po) {
        if (po == null) {
            return null;
        }
        return new Role(po.getId(), po.getName(), po.getDescription());
    }
}
