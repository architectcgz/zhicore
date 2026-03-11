package com.zhicore.user.application.service;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.result.PageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.common.util.QueryParamValidator;
import com.zhicore.user.application.assembler.UserAssembler;
import com.zhicore.user.application.query.view.UserManageView;
import com.zhicore.user.application.sentinel.UserSentinelHandlers;
import com.zhicore.user.application.sentinel.UserSentinelResources;
import com.zhicore.user.domain.model.User;
import com.zhicore.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 管理侧用户查询服务。
 *
 * 负责 admin 读链路的参数校验、查询和视图组装，不承载任何写操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManageQueryService {

    private final UserRepository userRepository;

    /**
     * 查询用户列表。
     *
     * @param keyword 关键词
     * @param status 状态
     * @param page 页码
     * @param size 每页大小
     * @return 用户分页列表
     */
    @SentinelResource(
            value = UserSentinelResources.QUERY_USERS,
            blockHandlerClass = UserSentinelHandlers.class,
            blockHandler = "handleQueryUsersBlocked"
    )
    @Transactional(readOnly = true)
    public PageResult<UserManageView> queryUsers(String keyword, String status, int page, int size) {
        try {
            keyword = QueryParamValidator.validateKeyword(keyword);
            status = QueryParamValidator.validateStatus(status);
            page = QueryParamValidator.validatePage(page);
            size = QueryParamValidator.validateSize(size);

            int offset = QueryParamValidator.calculateOffset(page, size);
            List<User> users = userRepository.findByConditions(keyword, status, offset, size);
            long total = userRepository.countByConditions(keyword, status);
            List<UserManageView> views = users.stream()
                    .map(UserAssembler::toManageView)
                    .toList();

            log.debug("Query users: keyword={}, status={}, page={}, size={}, total={}",
                    keyword, status, page, size, total);
            return PageResult.of(page, size, total, views);
        } catch (Exception e) {
            log.error("Failed to query users: keyword={}, status={}, page={}, size={}",
                    keyword, status, page, size, e);
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "查询用户列表失败");
        }
    }
}
