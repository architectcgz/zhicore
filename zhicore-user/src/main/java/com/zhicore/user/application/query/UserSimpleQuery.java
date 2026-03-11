package com.zhicore.user.application.query;

import com.zhicore.user.application.query.view.UserSimpleView;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 用户轻量读模型查询。
 *
 * 面向 application 层暴露简要信息查询能力，避免把轻量读投影包装成 repository。
 */
public interface UserSimpleQuery {

    Optional<UserSimpleView> findById(Long userId);

    Map<Long, UserSimpleView> findByIds(Set<Long> userIds);

    Optional<Boolean> findStrangerMessageAllowed(Long userId);
}
