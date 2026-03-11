package com.zhicore.user.infrastructure.repository;

import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.repository.RoleRepository;
import com.zhicore.user.infrastructure.repository.mapper.RoleMapper;
import com.zhicore.user.infrastructure.repository.po.RolePO;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
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
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

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
    public Map<Long, Set<Role>> findByUserIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        String sql = """
                SELECT ur.user_id, r.id, r.name, r.description
                FROM user_roles ur
                INNER JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id IN (:userIds)
                """;

        Map<Long, Set<Role>> rolesByUserId = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query(sql, new MapSqlParameterSource("userIds", userIds), rs -> {
            Long userId = rs.getLong("user_id");
            Role role = new Role(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("description")
            );
            rolesByUserId.computeIfAbsent(userId, key -> new LinkedHashSet<>()).add(role);
        });
        return rolesByUserId;
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
