package com.blog.user.domain.repository;

import com.blog.user.domain.model.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 用户仓储接口
 *
 * @author Blog Team
 */
public interface UserRepository {

    /**
     * 根据ID查询用户
     *
     * @param userId 用户ID
     * @return 用户
     */
    Optional<User> findById(Long userId);

    /**
     * 根据ID集合批量查询用户
     *
     * @param userIds 用户ID集合
     * @return 用户列表
     */
    List<User> findByIds(Set<Long> userIds);

    /**
     * 根据邮箱查询用户
     *
     * @param email 邮箱
     * @return 用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 根据用户名查询用户
     *
     * @param userName 用户名
     * @return 用户
     */
    Optional<User> findByUserName(String userName);

    /**
     * 保存用户
     *
     * @param user 用户
     * @return 保存后的用户
     */
    User save(User user);

    /**
     * 更新用户
     *
     * @param user 用户
     */
    void update(User user);

    /**
     * 检查邮箱是否存在
     *
     * @param email 邮箱
     * @return 是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 检查用户名是否存在
     *
     * @param userName 用户名
     * @return 是否存在
     */
    boolean existsByUserName(String userName);

    /**
     * 检查用户ID是否存在
     *
     * @param userId 用户ID
     * @return 是否存在
     */
    boolean existsById(Long userId);

    /**
     * 查询所有用户
     *
     * @return 用户列表
     */
    List<User> findAll();

    /**
     * 根据条件查询用户列表（分页）
     * 支持关键词（用户名或邮箱）、状态过滤
     *
     * @param keyword 关键词（用户名或邮箱），为 null 或空字符串时忽略此条件
     * @param status 状态，为 null 或空字符串时忽略此条件
     * @param offset 偏移量（从0开始）
     * @param limit 限制数量
     * @return 用户列表
     */
    List<User> findByConditions(String keyword, String status, int offset, int limit);

    /**
     * 根据条件统计用户数量
     * 支持关键词（用户名或邮箱）、状态过滤
     *
     * @param keyword 关键词（用户名或邮箱），为 null 或空字符串时忽略此条件
     * @param status 状态，为 null 或空字符串时忽略此条件
     * @return 用户数量
     */
    long countByConditions(String keyword, String status);

    /**
     * 使用乐观锁更新用户
     * 
     * 在 SQL 中原子递增 profile_version，确保版本号单调递增
     * 如果并发更新导致版本号不匹配，抛出 OptimisticLockException
     * 
     * 实现原理：
     * 1. 使用 WHERE profile_version = :currentVersion 作为乐观锁条件
     * 2. 使用 profile_version = profile_version + 1 原子递增版本号
     * 3. 使用 RETURNING profile_version 获取新版本号
     * 4. 如果更新影响行数为 0，说明版本号冲突，抛出异常
     * 
     * @param user 用户对象（必须包含当前的 profileVersion）
     * @return 更新后的用户（包含新的版本号）
     * @throws com.blog.user.domain.exception.OptimisticLockException 如果版本号冲突
     */
    User updateWithOptimisticLock(User user);
}
