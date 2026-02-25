package com.zhicore.content.domain.repository;

import com.zhicore.content.domain.model.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Tag 仓储接口
 * 
 * 负责 Tag 的持久化操作
 *
 * @author ZhiCore Team
 */
public interface TagRepository {

    /**
     * 保存标签
     * 
     * @param tag 标签实例
     * @return 保存后的标签
     */
    Tag save(Tag tag);

    /**
     * 根据 ID 查询标签
     * 
     * @param id 标签ID
     * @return 标签实例（Optional）
     */
    Optional<Tag> findById(Long id);

    /**
     * 根据 slug 查询标签（唯一）
     * 
     * @param slug 标签slug
     * @return 标签实例（Optional）
     */
    Optional<Tag> findBySlug(String slug);

    /**
     * 批量根据 slug 查询标签
     * 
     * @param slugs slug 列表
     * @return 标签列表
     */
    List<Tag> findBySlugIn(List<String> slugs);

    /**
     * 批量根据 ID 查询标签
     * 
     * @param ids ID 列表
     * @return 标签列表
     */
    List<Tag> findByIdIn(List<Long> ids);

    /**
     * 检查 slug 是否存在
     * 
     * @param slug 标签slug
     * @return 是否存在
     */
    boolean existsBySlug(String slug);

    /**
     * 分页查询所有标签
     * 
     * @param pageable 分页参数
     * @return 标签分页结果
     */
    Page<Tag> findAll(Pageable pageable);

    /**
     * 根据名称模糊搜索标签
     * 
     * @param keyword 关键词
     * @param limit 限制数量
     * @return 标签列表
     */
    List<Tag> searchByName(String keyword, int limit);

    /**
     * 获取热门标签（按文章数量排序）
     * 
     * 从 tag_stats 表查询，按 post_count 降序排序
     * 
     * @param limit 限制数量
     * @return 标签统计列表（包含 tag_id 和 post_count）
     */
    List<java.util.Map<String, Object>> findHotTags(int limit);

    /**
     * 根据 ID 删除标签
     * 
     * 注意：由于数据库设置了 ON DELETE CASCADE，删除标签会级联删除所有关联的 post_tags 记录
     * 
     * @param id 标签ID
     */
    void deleteById(Long id);

    /**
     * 批量查询文章的标签（避免 N+1 查询）
     * 
     * 用于文章列表场景，一次性加载多篇文章的标签信息
     * 
     * @param postIds 文章ID列表
     * @return Map<文章ID, 标签列表>
     */
    java.util.Map<Long, List<Tag>> findTagsByPostIds(List<Long> postIds);
}
