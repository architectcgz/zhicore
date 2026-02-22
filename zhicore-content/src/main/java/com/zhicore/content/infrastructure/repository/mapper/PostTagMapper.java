package com.zhicore.content.infrastructure.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhicore.content.infrastructure.repository.po.PostTagPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Post-Tag 关联 Mapper
 *
 * @author ZhiCore Team
 */
@Mapper
public interface PostTagMapper extends BaseMapper<PostTagPO> {

    /**
     * 批量插入关联
     * 
     * @param postTagList 关联列表
     * @return 插入数量
     */
    int insertBatch(@Param("list") List<PostTagPO> postTagList);

    /**
     * 查询文章的所有标签ID
     * 
     * @param postId 文章ID
     * @return 标签ID列表
     */
    @Select("SELECT tag_id FROM post_tags WHERE post_id = #{postId}")
    List<Long> selectTagIdsByPostId(@Param("postId") Long postId);

    /**
     * 查询标签下的所有文章ID
     * 
     * @param tagId 标签ID
     * @return 文章ID列表
     */
    @Select("SELECT post_id FROM post_tags WHERE tag_id = #{tagId}")
    List<Long> selectPostIdsByTagId(@Param("tagId") Long tagId);

    /**
     * 统计标签下的文章数量
     * 
     * @param tagId 标签ID
     * @return 文章数量
     */
    @Select("SELECT COUNT(*) FROM post_tags WHERE tag_id = #{tagId}")
    int countPostsByTagId(@Param("tagId") Long tagId);

    /**
     * 统计文章的标签数量
     * 
     * @param postId 文章ID
     * @return 标签数量
     */
    @Select("SELECT COUNT(*) FROM post_tags WHERE post_id = #{postId}")
    int countTagsByPostId(@Param("postId") Long postId);

    /**
     * 批量查询文章的标签ID（避免 N+1 查询）
     * 
     * @param postIds 文章ID列表
     * @return 关联列表
     */
    List<PostTagPO> selectByPostIds(@Param("postIds") List<Long> postIds);
}
