package com.blog.post.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.post.domain.repository.PostTagRepository;
import com.blog.post.infrastructure.repository.mapper.PostTagMapper;
import com.blog.post.infrastructure.repository.po.PostTagPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Post-Tag 关联仓储实现
 * 
 * 使用 MyBatis-Plus 实现 Post-Tag 关联的持久化操作
 *
 * @author Blog Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostTagRepositoryImpl implements PostTagRepository {

    private final PostTagMapper postTagMapper;

    @Override
    public void attach(Long postId, Long tagId) {
        // 检查是否已存在
        if (exists(postId, tagId)) {
            log.warn("Post-Tag relation already exists: postId={}, tagId={}", postId, tagId);
            return; // 幂等操作，直接返回
        }

        // 创建关联
        PostTagPO po = new PostTagPO(postId, tagId);
        postTagMapper.insert(po);
        
        log.debug("Attached tag {} to post {}", tagId, postId);
    }

    @Override
    public void attachBatch(Long postId, List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }

        // 过滤已存在的关联
        List<Long> existingTagIds = findTagIdsByPostId(postId);
        List<Long> newTagIds = tagIds.stream()
                .filter(tagId -> !existingTagIds.contains(tagId))
                .collect(Collectors.toList());

        if (newTagIds.isEmpty()) {
            log.debug("All tags already attached to post {}", postId);
            return;
        }

        // 批量创建关联
        List<PostTagPO> postTags = newTagIds.stream()
                .map(tagId -> new PostTagPO(postId, tagId))
                .collect(Collectors.toList());

        postTagMapper.insertBatch(postTags);
        
        log.debug("Attached {} tags to post {}", newTagIds.size(), postId);
    }

    @Override
    public void detach(Long postId, Long tagId) {
        LambdaQueryWrapper<PostTagPO> wrapper = new LambdaQueryWrapper<PostTagPO>()
                .eq(PostTagPO::getPostId, postId)
                .eq(PostTagPO::getTagId, tagId);
        
        int deleted = postTagMapper.delete(wrapper);
        
        if (deleted > 0) {
            log.debug("Detached tag {} from post {}", tagId, postId);
        } else {
            log.warn("Post-Tag relation not found: postId={}, tagId={}", postId, tagId);
        }
    }

    @Override
    public void detachAllByPostId(Long postId) {
        LambdaQueryWrapper<PostTagPO> wrapper = new LambdaQueryWrapper<PostTagPO>()
                .eq(PostTagPO::getPostId, postId);
        
        int deleted = postTagMapper.delete(wrapper);
        
        log.debug("Detached {} tags from post {}", deleted, postId);
    }

    @Override
    public List<Long> findTagIdsByPostId(Long postId) {
        return postTagMapper.selectTagIdsByPostId(postId);
    }

    @Override
    public List<Long> findPostIdsByTagId(Long tagId) {
        return postTagMapper.selectPostIdsByTagId(tagId);
    }

    @Override
    public org.springframework.data.domain.Page<Long> findPostIdsByTagId(Long tagId, Pageable pageable) {
        // 转换 Spring Data 的 Pageable 到 MyBatis-Plus 的 Page
        Page<PostTagPO> page = new Page<>(pageable.getPageNumber() + 1, pageable.getPageSize());
        
        LambdaQueryWrapper<PostTagPO> wrapper = new LambdaQueryWrapper<PostTagPO>()
                .eq(PostTagPO::getTagId, tagId)
                .select(PostTagPO::getPostId)
                .orderByDesc(PostTagPO::getCreatedAt);
        
        IPage<PostTagPO> result = postTagMapper.selectPage(page, wrapper);
        
        List<Long> postIds = result.getRecords().stream()
                .map(PostTagPO::getPostId)
                .collect(Collectors.toList());
        
        return new PageImpl<>(postIds, pageable, result.getTotal());
    }

    @Override
    public boolean exists(Long postId, Long tagId) {
        LambdaQueryWrapper<PostTagPO> wrapper = new LambdaQueryWrapper<PostTagPO>()
                .eq(PostTagPO::getPostId, postId)
                .eq(PostTagPO::getTagId, tagId);
        
        return postTagMapper.selectCount(wrapper) > 0;
    }

    @Override
    public int countPostsByTagId(Long tagId) {
        return postTagMapper.countPostsByTagId(tagId);
    }

    @Override
    public int countTagsByPostId(Long postId) {
        return postTagMapper.countTagsByPostId(postId);
    }

    @Override
    public java.util.Map<Long, List<Long>> findTagIdsByPostIds(List<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        // 批量查询所有关联
        List<PostTagPO> postTags = postTagMapper.selectByPostIds(postIds);

        // 按 postId 分组
        return postTags.stream()
                .collect(Collectors.groupingBy(
                        PostTagPO::getPostId,
                        Collectors.mapping(PostTagPO::getTagId, Collectors.toList())
                ));
    }
}
