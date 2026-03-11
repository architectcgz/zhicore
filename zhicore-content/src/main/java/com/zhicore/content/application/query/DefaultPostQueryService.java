package com.zhicore.content.application.query;

import com.zhicore.content.application.port.repo.PostRepository;
import com.zhicore.content.application.port.repo.PostStatsRepository;
import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.query.view.PostListItemView;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.PostStats;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.UserId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文章查询服务实现
 * 
 * 负责从多个数据源组装查询结果，不包含缓存逻辑
 * 缓存由 CacheAsidePostQuery 装饰器处理
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Service("postSourceQuery")
@RequiredArgsConstructor
public class DefaultPostQueryService implements PostQuery {
    
    private final PostRepository postRepository;
    private final PostContentStore contentStore;
    private final PostStatsRepository statsRepository;
    
    @Override
    public PostDetailView getDetail(PostId postId) {
        // 1. 获取元数据
        Post post = postRepository.load(postId);
        
        // 2. 获取内容（支持降级）
        Optional<PostBody> content = Optional.empty();
        boolean contentDegraded = false;
        try {
            content = contentStore.getContent(postId);
        } catch (Exception e) {
            log.warn("Failed to fetch content from MongoDB for postId: {}", postId, e);
            contentDegraded = true;
        }
        
        // 3. 获取统计（最终一致）
        Optional<PostStats> stats = statsRepository.findById(postId);
        PostStats postStats = stats.orElse(PostStats.empty(postId));
        
        // 4. 组装视图
        return PostDetailView.builder()
                .id(post.getId())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .coverImage(post.getCoverImageId())
                .content(content.map(PostBody::getContent).orElse(null))
                .contentDegraded(contentDegraded)
                .status(post.getStatus())
                .ownerSnapshot(post.getOwnerSnapshot())
                .tagIds(post.getTagIds())
                .viewCount(postStats.getViewCount())
                .likeCount(postStats.getLikeCount())
                .commentCount(postStats.getCommentCount())
                .shareCount(postStats.getShareCount())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .scheduledPublishAt(post.getScheduledAt())
                .publishedAt(post.getPublishedAt())
                .build();
    }
    
    @Override
    public List<PostListItemView> getLatestPosts(Pageable pageable) {
        List<Post> posts = postRepository.findLatest(pageable);
        return toListItemViews(posts);
    }
    
    @Override
    public List<PostListItemView> getPostsByAuthor(UserId authorId, Pageable pageable) {
        List<Post> posts = postRepository.findByAuthor(authorId, pageable);
        return toListItemViews(posts);
    }
    
    @Override
    public List<PostListItemView> getPostsByTag(TagId tagId, Pageable pageable) {
        List<Post> posts = postRepository.findByTag(tagId, pageable);
        return toListItemViews(posts);
    }
    
    /**
     * 将 Post 列表转换为 PostListItemView 列表
     * 
     * 批量查询 stats，避免 N+1 问题
     * 
     * @param posts 文章列表
     * @return 视图列表
     */
    private List<PostListItemView> toListItemViews(List<Post> posts) {
        if (posts.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 批量查询 stats，避免 N+1
        List<PostId> postIds = posts.stream()
                .map(Post::getId)
                .collect(Collectors.toList());
        Map<PostId, PostStats> statsMap = statsRepository.findByIds(postIds);
        
        return posts.stream()
                .map(post -> {
                    PostStats stats = statsMap.getOrDefault(post.getId(), PostStats.empty(post.getId()));
                    return PostListItemView.builder()
                            .id(post.getId())
                            .title(post.getTitle())
                            .excerpt(post.getExcerpt())
                            .coverImage(post.getCoverImageId())
                            .ownerSnapshot(post.getOwnerSnapshot())
                            .viewCount(stats.getViewCount())
                            .likeCount(stats.getLikeCount())
                            .commentCount(stats.getCommentCount())
                            .shareCount(stats.getShareCount())
                            .createdAt(post.getCreatedAt())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
