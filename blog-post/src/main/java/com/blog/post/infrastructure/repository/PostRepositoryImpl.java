package com.blog.post.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.util.DateTimeUtils;
import com.blog.post.domain.model.Post;
import com.blog.post.domain.model.PostStats;
import com.blog.post.domain.model.PostStatus;
import com.blog.post.domain.repository.PostRepository;
import com.blog.post.infrastructure.repository.mapper.PostMapper;
import com.blog.post.infrastructure.repository.mapper.PostStatsMapper;
import com.blog.post.infrastructure.repository.po.PostPO;
import com.blog.post.infrastructure.repository.po.PostStatsPO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 文章仓储实现
 *
 * @author Blog Team
 */
@Slf4j
@Repository("postRepositoryImpl")
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostMapper postMapper;
    private final PostStatsMapper postStatsMapper;

    @Override
    public void save(Post post) {
        PostPO po = toPO(post);
        postMapper.insert(po);

        // 初始化统计数据
        PostStatsPO statsPO = new PostStatsPO();
        statsPO.setPostId(post.getId());
        statsPO.setLikeCount(0);
        statsPO.setCommentCount(0);
        statsPO.setFavoriteCount(0);
        statsPO.setViewCount(0L);
        postStatsMapper.insert(statsPO);
    }

    @Override
    public void update(Post post) {
        PostPO po = toPO(post);
        postMapper.updateById(po);
    }

    @Override
    public Optional<Post> findById(Long id) {
        PostPO po = postMapper.selectById(id);
        if (po == null) {
            return Optional.empty();
        }
        PostStatsPO statsPO = postStatsMapper.selectById(id);
        return Optional.of(toDomain(po, statsPO));
    }

    @Override
    public Map<Long, Post> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PostPO> posts = postMapper.selectBatchIds(ids);
        List<PostStatsPO> stats = postStatsMapper.selectBatchIds(ids);
        
        Map<Long, PostStatsPO> statsMap = stats.stream()
                .collect(Collectors.toMap(PostStatsPO::getPostId, s -> s));
        
        return posts.stream()
                .collect(Collectors.toMap(
                        PostPO::getId,
                        po -> toDomain(po, statsMap.get(po.getId()))
                ));
    }

    @Override
    public List<Post> findByOwnerId(Long ownerId, PostStatus status, int offset, int limit) {
        Page<PostPO> page = new Page<>(offset / limit + 1, limit);
        LambdaQueryWrapper<PostPO> wrapper = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getOwnerId, ownerId)
                .eq(PostPO::getStatus, status.getCode())
                .orderByDesc(PostPO::getCreatedAt);
        
        Page<PostPO> result = postMapper.selectPage(page, wrapper);
        return toDomainList(result.getRecords());
    }

    @Override
    public List<Post> findByOwnerIdCursor(Long ownerId, PostStatus status, LocalDateTime cursor, int limit) {
        List<PostPO> posts = postMapper.findByOwnerIdCursor(ownerId, status.getCode(), cursor, limit);
        return toDomainList(posts);
    }

    @Override
    public List<Post> findPublished(int offset, int limit) {
        Page<PostPO> page = new Page<>(offset / limit + 1, limit);
        LambdaQueryWrapper<PostPO> wrapper = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getStatus, PostStatus.PUBLISHED.getCode())
                .orderByDesc(PostPO::getPublishedAt);
        
        Page<PostPO> result = postMapper.selectPage(page, wrapper);
        return toDomainList(result.getRecords());
    }

    @Override
    public List<Post> findPublishedCursor(LocalDateTime cursor, int limit) {
        List<PostPO> posts = postMapper.findPublishedCursor(cursor, limit);
        return toDomainList(posts);
    }

    @Override
    public List<Post> findScheduledPostsDue(LocalDateTime now) {
        List<PostPO> posts = postMapper.findScheduledPostsDue(now);
        return toDomainList(posts);
    }

    @Override
    public int countByOwnerId(Long ownerId, PostStatus status) {
        LambdaQueryWrapper<PostPO> wrapper = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getOwnerId, ownerId)
                .eq(PostPO::getStatus, status.getCode());
        return Math.toIntExact(postMapper.selectCount(wrapper));
    }

    @Override
    public long countPublished() {
        LambdaQueryWrapper<PostPO> wrapper = new LambdaQueryWrapper<PostPO>()
                .eq(PostPO::getStatus, PostStatus.PUBLISHED.getCode());
        return postMapper.selectCount(wrapper);
    }

    @Override
    public void delete(Long id) {
        postMapper.deleteById(id);
        postStatsMapper.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return postMapper.selectById(id) != null;
    }

    @Override
    public List<Post> findByConditions(String keyword, String status, Long authorId, int offset, int limit) {
        List<PostPO> posts = postMapper.selectByConditions(keyword, status, authorId, offset, limit);
        return toDomainList(posts);
    }

    @Override
    public long countByConditions(String keyword, String status, Long authorId) {
        return postMapper.countByConditions(keyword, status, authorId);
    }

    @Override
    public int updateAuthorInfo(Long userId, String nickname, String avatarId, Long version) {
        log.debug("批量更新作者信息: userId={}, nickname={}, avatarId={}, version={}", 
                userId, nickname, avatarId, version);
        
        int updatedCount = postMapper.updateAuthorInfo(userId, nickname, avatarId, version);
        
        log.debug("作者信息更新完成: userId={}, 更新文章数={}", userId, updatedCount);
        return updatedCount;
    }

    @Override
    public List<Post> findByOwnerNameAndVersion(String ownerName, Long version, int limit) {
        log.debug("根据作者昵称和版本号查询文章: ownerName={}, version={}, limit={}", 
                ownerName, version, limit);
        
        List<PostPO> posts = postMapper.findByOwnerNameAndVersion(ownerName, version, limit);
        List<Post> result = toDomainList(posts);
        
        log.debug("查询完成: ownerName={}, 找到文章数={}", ownerName, result.size());
        return result;
    }

    // ==================== 转换方法 ====================

    private PostPO toPO(Post post) {
        PostPO po = new PostPO();
        po.setId(post.getId());
        po.setOwnerId(post.getOwnerId());
        po.setTitle(post.getTitle());
        po.setExcerpt(post.getExcerpt());
        po.setCoverImageId(post.getCoverImageId());
        po.setStatus(post.getStatus().getCode());
        po.setTopicId(post.getTopicId());
        po.setPublishedAt(DateTimeUtils.toOffsetDateTime(post.getPublishedAt()));
        po.setScheduledAt(DateTimeUtils.toOffsetDateTime(post.getScheduledAt()));
        po.setCreatedAt(DateTimeUtils.toOffsetDateTime(post.getCreatedAt()));
        po.setUpdatedAt(DateTimeUtils.toOffsetDateTime(post.getUpdatedAt()));
        return po;
    }

    private Post toDomain(PostPO po, PostStatsPO statsPO) {
        PostStats stats = statsPO != null
                ? new PostStats(statsPO.getLikeCount(), statsPO.getCommentCount(),
                        statsPO.getFavoriteCount(), statsPO.getViewCount())
                : PostStats.empty();

        return Post.reconstitute(
                po.getId(),
                po.getOwnerId(),
                po.getTitle(),
                po.getExcerpt(),
                po.getCoverImageId(),
                PostStatus.fromCode(po.getStatus()),
                po.getTopicId(),
                DateTimeUtils.toLocalDateTime(po.getPublishedAt()),
                DateTimeUtils.toLocalDateTime(po.getScheduledAt()),
                DateTimeUtils.toLocalDateTime(po.getCreatedAt()),
                DateTimeUtils.toLocalDateTime(po.getUpdatedAt()),
                false, // isArchived - default to false
                stats
        );
    }

    private List<Post> toDomainList(List<PostPO> posts) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = posts.stream().map(PostPO::getId).collect(Collectors.toList());
        List<PostStatsPO> stats = postStatsMapper.selectBatchIds(ids);
        Map<Long, PostStatsPO> statsMap = stats.stream()
                .collect(Collectors.toMap(PostStatsPO::getPostId, s -> s));
        
        return posts.stream()
                .map(po -> toDomain(po, statsMap.get(po.getId())))
                .collect(Collectors.toList());
    }
}
