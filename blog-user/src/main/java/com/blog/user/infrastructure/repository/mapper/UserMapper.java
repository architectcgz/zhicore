package com.blog.user.infrastructure.repository.mapper;

import com.blog.user.infrastructure.repository.po.UserPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 用户 Mapper
 *
 * @author Blog Team
 */
@Mapper
public interface UserMapper extends BaseMapper<UserPO> {

    /**
     * 根据邮箱查询用户
     */
    @Select("SELECT * FROM users WHERE email = #{email}")
    UserPO selectByEmail(@Param("email") String email);

    /**
     * 根据用户名查询用户
     */
    @Select("SELECT * FROM users WHERE username = #{userName}")
    UserPO selectByUserName(@Param("userName") String userName);

    /**
     * 根据ID集合批量查询用户
     */
    @Select("<script>SELECT * FROM users WHERE id IN <foreach collection='userIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<UserPO> selectByIds(@Param("userIds") Set<Long> userIds);

    /**
     * 检查邮箱是否存在
     */
    @Select("SELECT EXISTS(SELECT 1 FROM users WHERE email = #{email})")
    boolean existsByEmail(@Param("email") String email);

    /**
     * 检查用户名是否存在
     */
    @Select("SELECT EXISTS(SELECT 1 FROM users WHERE username = #{userName})")
    boolean existsByUserName(@Param("userName") String userName);

    /**
     * 查询所有用户
     */
    @Select("SELECT * FROM users WHERE deleted = false ORDER BY created_at DESC")
    List<UserPO> selectAll();

    /**
     * 根据条件查询用户列表（分页）
     * 支持关键词（用户名或邮箱）、状态过滤
     *
     * @param keyword 关键词（用户名或邮箱）
     * @param status 状态
     * @param offset 偏移量
     * @param limit 限制数量
     * @return 用户列表
     */
    List<UserPO> selectByConditions(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("offset") int offset,
                                     @Param("limit") int limit);

    /**
     * 根据条件统计用户数量
     * 支持关键词（用户名或邮箱）、状态过滤
     *
     * @param keyword 关键词（用户名或邮箱）
     * @param status 状态
     * @return 用户数量
     */
    long countByConditions(@Param("keyword") String keyword,
                           @Param("status") String status);
}
