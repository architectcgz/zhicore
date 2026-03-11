package com.zhicore.user.infrastructure.repository;

import com.zhicore.common.util.DateTimeUtils;
import com.zhicore.user.domain.exception.OptimisticLockException;
import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.model.UserStatus;
import com.zhicore.user.domain.repository.RoleRepository;
import com.zhicore.user.domain.repository.UserRepository;
import com.zhicore.user.infrastructure.repository.mapper.UserMapper;
import com.zhicore.user.infrastructure.repository.po.UserPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用户仓储实现
 *
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;
    private final RoleRepository roleRepository;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Override
    public Optional<User> findById(Long userId) {
        UserPO po = userMapper.selectById(userId);
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public List<User> findByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<UserPO> poList = userMapper.selectByIds(userIds);
        Map<Long, Set<Role>> rolesByUserId = roleRepository.findByUserIds(userIds);
        List<User> users = new ArrayList<>();
        for (UserPO po : poList) {
            User user = toDomain(po, rolesByUserId.getOrDefault(po.getId(), Set.of()));
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public Optional<User> findByEmail(String email) {
        UserPO po = userMapper.selectByEmail(email);
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public Optional<User> findByUserName(String userName) {
        UserPO po = userMapper.selectByUserName(userName);
        return Optional.ofNullable(toDomain(po));
    }

    @Override
    public User save(User user) {
        UserPO po = toPO(user);
        userMapper.insert(po);
        
        // 保存用户角色关联
        for (Role role : user.getRoles()) {
            roleRepository.saveUserRole(user.getId(), role.getId());
        }
        
        return user;
    }

    @Override
    public void update(User user) {
        UserPO po = toPO(user);
        userMapper.updateById(po);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.existsByEmail(email);
    }

    @Override
    public boolean existsByUserName(String userName) {
        return userMapper.existsByUserName(userName);
    }

    @Override
    public boolean existsById(Long userId) {
        return userMapper.selectById(userId) != null;
    }

    @Override
    public List<User> findAll() {
        List<UserPO> poList = userMapper.selectAll();
        List<User> users = new ArrayList<>();
        for (UserPO po : poList) {
            User user = toDomain(po);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public List<User> findByConditions(String keyword, String status, int offset, int limit) {
        List<UserPO> poList = userMapper.selectByConditions(keyword, status, offset, limit);
        List<User> users = new ArrayList<>();
        for (UserPO po : poList) {
            User user = toDomain(po);
            if (user != null) {
                users.add(user);
            }
        }
        return users;
    }

    @Override
    public long countByConditions(String keyword, String status) {
        return userMapper.countByConditions(keyword, status);
    }

    @Override
    public User updateWithOptimisticLock(User user) {
        String sql = """
            UPDATE users 
            SET nick_name = :nickName,
                avatar_id = :avatarId,
                bio = :bio,
                profile_version = profile_version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = :id 
              AND profile_version = :currentVersion
            RETURNING profile_version
            """;
        
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", user.getId())
            .addValue("nickName", user.getNickName())
            .addValue("avatarId", user.getAvatarId())
            .addValue("bio", user.getBio())
            .addValue("currentVersion", user.getProfileVersion());
        
        try {
            // 执行更新并获取新版本号
            Long newVersion = namedParameterJdbcTemplate.queryForObject(
                sql, 
                params, 
                Long.class
            );
            
            if (newVersion == null) {
                throw new OptimisticLockException(
                    "并发更新冲突：用户资料已被其他事务修改，请重试"
                );
            }
            
            // 更新对象的版本号
            user.setProfileVersion(newVersion);
            
            log.debug("User updated with optimistic lock: userId={}, newVersion={}", 
                user.getId(), newVersion);
            
            return user;
            
        } catch (EmptyResultDataAccessException e) {
            throw new OptimisticLockException(
                "并发更新冲突：用户资料已被其他事务修改，请重试",
                e
            );
        }
    }

    /**
     * PO 转领域对象
     */
    private User toDomain(UserPO po) {
        if (po == null) {
            return null;
        }

        // 查询用户角色
        Set<Role> roles = roleRepository.findByUserId(po.getId());
        return toDomain(po, roles);
    }

    private User toDomain(UserPO po, Set<Role> roles) {
        if (po == null) {
            return null;
        }

        UserStatus status = Boolean.TRUE.equals(po.getIsActive()) ? UserStatus.ACTIVE : UserStatus.DISABLED;

        return User.reconstitute(new User.Snapshot(
                po.getId(),
                po.getUserName(),
                po.getNickName(),
                po.getEmail(),
                po.getPasswordHash(),
                po.getAvatarId(),
                po.getBio(),
                status,
                Boolean.TRUE.equals(po.getEmailConfirmed()),
                po.getAllowStrangerMessage() == null || po.getAllowStrangerMessage(),
                roles,
                po.getProfileVersion(),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()),
                DateTimeUtils.toLocalDateTime(po.getUpdatedAt())
        ));
    }

    /**
     * 领域对象转 PO
     */
    private UserPO toPO(User user) {
        UserPO po = new UserPO();
        po.setId(user.getId());
        po.setUserName(user.getUserName());
        po.setNickName(user.getNickName());
        po.setEmail(user.getEmail());
        po.setPasswordHash(user.getPasswordHash());
        po.setAvatarId(user.getAvatarId());
        po.setBio(user.getBio());
        po.setProfileVersion(user.getProfileVersion());
        po.setIsActive(user.getStatus() == UserStatus.ACTIVE);
        po.setEmailConfirmed(user.isEmailConfirmed());
        po.setAllowStrangerMessage(user.isStrangerMessageAllowed());
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(user.getCreatedAt()));
        po.setUpdatedAt(DateTimeUtils.toOffsetDateTime(user.getUpdatedAt()));
        po.setDeleted(false);
        return po;
    }
}
