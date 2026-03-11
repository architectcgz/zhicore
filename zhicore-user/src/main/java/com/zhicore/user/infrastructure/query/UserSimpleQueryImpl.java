package com.zhicore.user.infrastructure.query;

import com.zhicore.user.application.query.UserSimpleQuery;
import com.zhicore.user.application.query.view.UserSimpleView;
import com.zhicore.user.infrastructure.repository.mapper.UserMapper;
import com.zhicore.user.infrastructure.repository.po.UserPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 基于 MyBatis 的用户轻量读模型查询实现。
 */
@Component
@RequiredArgsConstructor
public class UserSimpleQueryImpl implements UserSimpleQuery {

    private final UserMapper userMapper;

    @Override
    public Optional<UserSimpleView> findById(Long userId) {
        return Optional.ofNullable(toView(userMapper.selectSimpleById(userId)));
    }

    @Override
    public Map<Long, UserSimpleView> findByIds(Set<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, UserSimpleView> result = new LinkedHashMap<>();
        for (UserPO userPO : userMapper.selectSimpleByIds(userIds)) {
            UserSimpleView view = toView(userPO);
            if (view != null) {
                result.put(view.getId(), view);
            }
        }
        return result;
    }

    @Override
    public Optional<Boolean> findStrangerMessageAllowed(Long userId) {
        return Optional.ofNullable(userMapper.selectAllowStrangerMessageById(userId))
                .map(value -> value == null || value);
    }

    private UserSimpleView toView(UserPO userPO) {
        if (userPO == null) {
            return null;
        }

        UserSimpleView view = new UserSimpleView();
        view.setId(userPO.getId());
        view.setUserName(userPO.getUserName());
        view.setNickname(userPO.getNickName());
        view.setAvatarId(userPO.getAvatarId());
        view.setProfileVersion(userPO.getProfileVersion());
        return view;
    }
}
