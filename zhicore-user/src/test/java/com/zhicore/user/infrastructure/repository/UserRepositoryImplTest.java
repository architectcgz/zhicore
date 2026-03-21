package com.zhicore.user.infrastructure.repository;

import com.zhicore.user.domain.model.Role;
import com.zhicore.user.domain.repository.RoleRepository;
import com.zhicore.user.infrastructure.repository.mapper.UserMapper;
import com.zhicore.user.infrastructure.repository.po.UserPO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserRepositoryImpl 测试")
class UserRepositoryImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @InjectMocks
    private UserRepositoryImpl userRepository;

    @Test
    @DisplayName("findByConditions 应批量加载角色而不是逐条查询")
    void shouldBatchLoadRolesWhenFindingUsersByConditions() {
        UserPO first = buildUser(101L, "alice");
        UserPO second = buildUser(102L, "bob");
        when(userMapper.selectByConditions(null, null, 0, 5)).thenReturn(List.of(first, second));
        when(roleRepository.findByUserIds(Set.of(101L, 102L))).thenReturn(Map.of(
                101L, Set.of(new Role(1, "ADMIN", "管理员")),
                102L, Set.of(new Role(2, "USER", "普通用户"))
        ));

        var users = userRepository.findByConditions(null, null, 0, 5);

        assertEquals(2, users.size());
        assertIterableEquals(List.of("ADMIN"), users.get(0).getRoles().stream().map(Role::getName).toList());
        assertIterableEquals(List.of("USER"), users.get(1).getRoles().stream().map(Role::getName).toList());
        verify(roleRepository).findByUserIds(Set.of(101L, 102L));
        verify(roleRepository, never()).findByUserId(101L);
        verify(roleRepository, never()).findByUserId(102L);
    }

    private UserPO buildUser(Long id, String username) {
        UserPO po = new UserPO();
        po.setId(id);
        po.setUserName(username);
        po.setNickName(username);
        po.setEmail(username + "@example.com");
        po.setPasswordHash("hashed");
        po.setIsActive(true);
        po.setEmailConfirmed(true);
        po.setAllowStrangerMessage(true);
        po.setProfileVersion(0L);
        po.setDeleted(false);
        po.setCreatedAt(OffsetDateTime.now());
        po.setUpdatedAt(OffsetDateTime.now());
        return po;
    }
}
