package com.zhicore.content.application.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.api.client.UserServiceClient;
import com.zhicore.api.dto.post.PostDTO;
import com.zhicore.api.dto.user.UserSimpleDTO;
import com.zhicore.api.event.post.PostDeletedEvent;
import com.zhicore.api.event.post.PostPublishedEvent;
import com.zhicore.api.event.post.PostScheduleExecuteEvent;
import com.zhicore.api.event.post.PostScheduledEvent;
import com.zhicore.api.event.post.PostUpdatedEvent;
import com.zhicore.api.event.post.AuthorInfoCompensationEvent;
import com.zhicore.common.exception.BusinessException;
import com.zhicore.common.exception.ForbiddenException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.common.result.HybridPageRequest;
import com.zhicore.common.result.HybridPageResult;
import com.zhicore.common.result.ResultCode;
import com.zhicore.content.application.assembler.PostAssembler;
import com.zhicore.content.application.dto.PostBriefVO;
import com.zhicore.content.application.dto.PostVO;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.domain.constant.PostConstants;
import com.zhicore.content.domain.model.Post;
import com.zhicore.content.domain.model.PostStatus;
import com.zhicore.content.domain.event.PostCreatedEvent;
import com.zhicore.content.domain.event.PostTagsUpdatedEvent;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.PostRepository;
import com.zhicore.content.domain.service.DraftManager;
import com.zhicore.content.domain.service.DualStorageManager;
import com.zhicore.content.domain.service.TagDomainService;
import com.zhicore.content.infrastructure.mq.PostEventPublisher;
import com.zhicore.content.domain.repository.PostTagRepository;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.infrastructure.feign.ZhiCoreUploadClient;
import com.zhicore.content.interfaces.dto.request.CreatePostRequest;
import com.zhicore.content.interfaces.dto.request.UpdatePostRequest;
import com.zhicore.content.infrastructure.mongodb.document.PostContent;
import com.zhicore.content.infrastructure.mongodb.document.PostDraft;
import com.zhicore.content.interfaces.dto.request.SaveDraftRequest;
import com.zhicore.content.interfaces.dto.response.DraftVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章应用服务
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostApplicationService {

    private final PostRepository postRepository;
    private final PostEventPublisher eventPublisher;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final DualStorageManager dualStorageManager;
    private final DraftManager draftManager;
    private final TagDomainService tagDomainService;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final ZhiCoreUploadClient ZhiCoreUploadClient;
    private final UserServiceClient userServiceClient;

    /**
     * 创建文章（草稿）
     * 使用双写机制：同时写入 PostgreSQL（元数据）和 MongoDB（内容）
     *
     * @param userId 用户ID
     * @param request 创建请求
     * @return 文章ID
     */
    @Transactional
    public Long createPost(Long userId, CreatePostRequest request) {
        // 生成文章ID
        ApiResponse<Long> idResponse = idGeneratorFeignClient.generateSnowflakeId();
        if (!idResponse.isSuccess() || idResponse.getData() == null) {
            log.error("Failed to generate post ID: {}", idResponse.getMessage());
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "生成文章ID失败");
        }
        Long postId = idResponse.getData();

        // 创建草稿
        Post post = Post.createDraft(postId, userId, request.getTitle());
        
        // 填充作者信息（冗余存储）
        fillAuthorInfo(post, userId);
        
        // 设置话题
        if (request.getTopicId() != null) {
            post.setTopic(request.getTopicId());
        }

        // 设置封面图（验证并存储 fileId）
        if (request.getCoverImageId() != null && !request.getCoverImageId().isEmpty()) {
            validateFileId(request.getCoverImageId());
            post.setCoverImage(request.getCoverImageId());
        }

        // 创建 MongoDB 内容文档
        PostContent content =
            new PostContent();
        content.setContentType("markdown");
        content.setRaw(request.getContent());
        // HTML 和 text 将由 ContentEnricher 自动生成

        // 处理标签（在事务内）
        List<Long> tagIds = null;
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            // 验证标签数量
            if (request.getTags().size() > 10) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "单篇文章最多只能添加10个标签");
            }
            
            // 查找或创建标签
            List<Tag> tags = tagDomainService.findOrCreateBatch(request.getTags());
            tagIds = tags.stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());
        }

        // 使用双写机制保存（三阶段提交）
        dualStorageManager.createPost(post, content);

        // 创建 Post-Tag 关联（在同一事务中）
        if (tagIds != null && !tagIds.isEmpty()) {
            postTagRepository.attachBatch(postId, tagIds);
            log.info("Post tags attached: postId={}, tagCount={}", postId, tagIds.size());
        }

        // 发布 PostCreated 事件（用于 MongoDB 同步）
        List<String> tagIdStrings = tagIds != null ? 
            tagIds.stream().map(String::valueOf).collect(Collectors.toList()) : 
            Collections.emptyList();
        
        eventPublisher.publish(new PostCreatedEvent(
            postId.toString(),
            post.getTitle(),
            request.getContent(),
            post.getExcerpt(),
            userId.toString(),
            post.getOwnerName(), // 使用填充的作者名称
            tagIdStrings,
            request.getTopicId() != null ? request.getTopicId().toString() : null,
            null, // categoryName
            post.getStatus().name(),
            post.getPublishedAt(),
            post.getCreatedAt(),
            LocalDateTime.now()
        ));

        log.info("Post created with dual storage: postId={}, userId={}, ownerName={}", 
            postId, userId, post.getOwnerName());
        return postId;
    }

    /**
     * 更新文章
     * 使用双写机制：同时更新 PostgreSQL（元数据）和 MongoDB（内容）
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @param request 更新请求
     */
    @Transactional
    public void updatePost(Long userId, Long postId, UpdatePostRequest request) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 如果更新封面图，删除旧的封面图
        if (request.getCoverImageId() != null && !request.getCoverImageId().isEmpty()) {
            // 验证新的 fileId 格式
            validateFileId(request.getCoverImageId());
            
            // 如果有旧的封面图且与新的不同，删除旧的
            String oldCoverImageId = post.getCoverImageId();
            if (oldCoverImageId != null && !oldCoverImageId.isEmpty() 
                    && !oldCoverImageId.equals(request.getCoverImageId())) {
                try {
                    ZhiCoreUploadClient.deleteFile(oldCoverImageId);
                    log.info("删除旧封面图: postId={}, fileId={}", postId, oldCoverImageId);
                } catch (Exception e) {
                    log.error("删除旧封面图失败: postId={}, fileId={}, error={}", 
                        postId, oldCoverImageId, e.getMessage(), e);
                    // 不影响主流程，继续执行
                }
            }
            
            post.setCoverImage(request.getCoverImageId());
        }

        // 更新内容
        post.updateContent(request.getTitle(), request.getContent());
        
        // 更新话题
        if (request.getTopicId() != null) {
            post.setTopic(request.getTopicId());
        }

        // 创建 MongoDB 内容文档
        PostContent content =
            new PostContent();
        content.setContentType("markdown");
        content.setRaw(request.getContent());
        // HTML 和 text 将由 ContentEnricher 自动生成

        // 处理标签更新（如果提供了标签列表）
        if (request.getTags() != null) {
            // 获取旧标签
            List<Long> oldTagIds = postTagRepository.findTagIdsByPostId(postId);
            
            // 删除旧关联
            postTagRepository.detachAllByPostId(postId);
            
            // 创建新关联
            List<Long> newTagIds = Collections.emptyList();
            if (!request.getTags().isEmpty()) {
                List<Tag> tags = tagDomainService.findOrCreateBatch(request.getTags());
                newTagIds = tags.stream()
                        .map(Tag::getId)
                        .collect(Collectors.toList());
                postTagRepository.attachBatch(postId, newTagIds);
                log.info("Post tags updated: postId={}, tagCount={}", postId, newTagIds.size());
            } else {
                log.info("Post tags cleared: postId={}", postId);
            }
            
            // 发布 PostTagsUpdated 事件
            List<String> oldTagIdStrings = oldTagIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            List<String> newTagIdStrings = newTagIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
            
            eventPublisher.publish(new PostTagsUpdatedEvent(
                postId.toString(),
                oldTagIdStrings,
                newTagIdStrings,
                LocalDateTime.now(),
                LocalDateTime.now()
            ));
        }

        // 使用双写机制更新
        dualStorageManager.updatePost(post, content);

        // 如果已发布，发布更新事件
        if (post.isPublished()) {
            eventPublisher.publish(new PostUpdatedEvent(
                    postId, post.getTitle(), content.getRaw(), post.getExcerpt(), Collections.emptyList()));
        }

        log.info("Post updated with dual storage: postId={}, userId={}", postId, userId);
    }

    /**
     * 替换文章的所有标签
     * 
     * 流程：
     * 1. 验证用户权限
     * 2. 查询文章
     * 3. 处理新标签（查找或创建）
     * 4. 删除旧关联
     * 5. 创建新关联
     * 6. 更新缓存（通过事件）
     * 7. 发布事件
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @param tagNames 新的标签名称列表
     */
    @Transactional
    public void replacePostTags(Long userId, Long postId, List<String> tagNames) {
        // 验证用户权限
        Post post = getPostAndCheckOwnership(postId, userId);

        // 验证标签数量
        if (tagNames != null && tagNames.size() > 10) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单篇文章最多只能添加10个标签");
        }

        // 删除旧关联
        List<Long> oldTagIds = postTagRepository.findTagIdsByPostId(postId);
        postTagRepository.detachAllByPostId(postId);
        log.info("Old post tags detached: postId={}", postId);

        // 创建新关联
        List<Long> newTagIds = Collections.emptyList();
        if (tagNames != null && !tagNames.isEmpty()) {
            // 查找或创建标签
            List<Tag> tags = tagDomainService.findOrCreateBatch(tagNames);
            newTagIds = tags.stream()
                    .map(Tag::getId)
                    .collect(Collectors.toList());
            
            // 批量创建关联
            postTagRepository.attachBatch(postId, newTagIds);
            log.info("New post tags attached: postId={}, tagCount={}", postId, newTagIds.size());
        }

        // 发布 PostTagsUpdated 事件（用于缓存失效和 MongoDB/ES 同步）
        List<String> oldTagIdStrings = oldTagIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        List<String> newTagIdStrings = newTagIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
        
        eventPublisher.publish(new PostTagsUpdatedEvent(
            postId.toString(),
            oldTagIdStrings,
            newTagIdStrings,
            LocalDateTime.now(),
            LocalDateTime.now()
        ));

        log.info("Post tags replaced: postId={}, userId={}, newTagCount={}", 
                postId, userId, tagNames != null ? tagNames.size() : 0);
    }

    /**
     * 发布文章
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void publishPost(Long userId, Long postId) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 发布
        post.publish();

        // 保存
        postRepository.update(post);

        // 发布事件
        eventPublisher.publish(new PostPublishedEvent(
                postId, userId, post.getTitle(), post.getExcerpt(), Collections.emptyList()));

        log.info("Post published: postId={}, userId={}", postId, userId);
    }

    /**
     * 撤回文章（取消发布）
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void unpublishPost(Long userId, Long postId) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 撤回
        post.unpublish();

        // 保存
        postRepository.update(post);

        log.info("Post unpublished: postId={}, userId={}", postId, userId);
    }

    /**
     * 定时发布文章
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @param scheduledAt 定时发布时间
     */
    @Transactional
    public void schedulePublish(Long userId, Long postId, LocalDateTime scheduledAt) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 设置定时发布
        post.schedulePublish(scheduledAt);

        // 保存
        postRepository.update(post);

        // 计算延迟级别
        int delayLevel = calculateDelayLevel(scheduledAt);

        // 发布延迟消息
        eventPublisher.publishDelayed(
                new PostScheduleExecuteEvent(postId, userId), 
                delayLevel);

        // 发布定时发布事件（用于通知等）
        eventPublisher.publish(new PostScheduledEvent(postId, userId, scheduledAt));

        log.info("Post scheduled: postId={}, userId={}, scheduledAt={}", postId, userId, scheduledAt);
    }

    /**
     * 取消定时发布
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void cancelSchedule(Long userId, Long postId) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 取消定时
        post.cancelSchedule();

        // 保存
        postRepository.update(post);

        log.info("Post schedule cancelled: postId={}, userId={}", postId, userId);
    }

    /**
     * 执行定时发布（由消息消费者调用）
     *
     * @param postId 文章ID
     */
    @Transactional
    public void executeScheduledPublish(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        // 检查是否仍为定时发布状态
        if (post.getStatus() != PostStatus.SCHEDULED) {
            log.info("Post is not in scheduled status, skip: postId={}, status={}", 
                    postId, post.getStatus());
            return;
        }

        // 执行发布
        post.executeScheduledPublish();

        // 保存
        postRepository.update(post);

        // 发布事件
        eventPublisher.publish(new PostPublishedEvent(
                postId, post.getOwnerId(), post.getTitle(), post.getExcerpt(), Collections.emptyList()));

        log.info("Scheduled post published: postId={}", postId);
    }

    /**
     * 删除文章（软删除）
     * 使用双删除机制：同时删除 PostgreSQL 和 MongoDB 中的数据
     *
     * @param userId 用户ID
     * @param postId 文章ID
     */
    @Transactional
    public void deletePost(Long userId, Long postId) {
        Post post = getPostAndCheckOwnership(postId, userId);

        // 删除封面图
        if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
            try {
                ZhiCoreUploadClient.deleteFile(post.getCoverImageId());
                log.info("删除封面图: postId={}, fileId={}", postId, post.getCoverImageId());
            } catch (Exception e) {
                log.error("删除封面图失败: postId={}, fileId={}, error={}", 
                    postId, post.getCoverImageId(), e.getMessage(), e);
                // 不影响主流程，继续执行
            }
        }

        // 删除内容中的图片
        // Note: Content is now in MongoDB, we would need to fetch it to extract image URLs
        // For now, skip this step as it's not critical for deletion
        // TODO: Implement content image cleanup by fetching from MongoDB

        // 软删除
        post.delete();

        // 使用双删除机制
        dualStorageManager.deletePost(postId);

        // 发布删除事件
        eventPublisher.publish(new PostDeletedEvent(postId, userId));

        log.info("Post deleted with dual storage: postId={}, userId={}", postId, userId);
    }

    /**
     * 获取文章详情
     * 使用双存储查询:从 PostgreSQL 获取元数据，从 MongoDB 获取内容
     *
     * @param postId 文章ID
     * @return 文章视图对象
     */
    @Transactional(readOnly = true)
    public PostVO getPostById(Long postId) {
        // 使用双存储管理器获取完整详情（并行查询）
        DualStorageManager.PostDetail detail =
            dualStorageManager.getPostFullDetail(postId);
        
        Post post = detail.getPost();
        
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }

        if (post.isDeleted()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章已删除");
        }

        // 获取封面图 URL
        String coverImageUrl = null;
        if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
            coverImageUrl = getFileUrl(post.getCoverImageId());
        }

        // 如果内容不可用（降级场景），返回不包含内容的 VO
        if (detail.isContentUnavailable() || detail.getContent() == null) {
            log.warn("Post content unavailable for postId: {}, returning metadata only", postId);
            PostVO vo = PostAssembler.toVO(post);
            vo.setCoverImageUrl(coverImageUrl);
            // 填充作者信息
            enrichPostWithAuthorInfo(vo, post);
            return vo;
        }

        // 返回包含完整内容的 VO
        PostVO vo = PostAssembler.toVOWithContent(post, detail.getContent());
        vo.setCoverImageUrl(coverImageUrl);
        // 填充作者信息
        enrichPostWithAuthorInfo(vo, post);
        return vo;
    }

    /**
     * 获取用户的文章详情（包括草稿）
     * 使用双存储查询：从 PostgreSQL 获取元数据，从 MongoDB 获取内容
     *
     * @param userId 用户ID
     * @param postId 文章ID
     * @return 文章视图对象
     */
    @Transactional(readOnly = true)
    public PostVO getUserPostById(Long userId, Long postId) {
        // 使用双存储管理器获取完整详情（并行查询）
        DualStorageManager.PostDetail detail =
            dualStorageManager.getPostFullDetail(postId);
        
        Post post = detail.getPost();
        
        if (post == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章不存在");
        }

        if (!post.isOwnedBy(userId)) {
            throw new ForbiddenException("无权访问此文章");
        }

        if (post.isDeleted()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章已删除");
        }

        // 获取封面图 URL
        String coverImageUrl = null;
        if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
            coverImageUrl = getFileUrl(post.getCoverImageId());
        }

        // 如果内容不可用（降级场景），返回不包含内容的 VO
        if (detail.isContentUnavailable() || detail.getContent() == null) {
            log.warn("Post content unavailable for postId: {}, returning metadata only", postId);
            PostVO vo = PostAssembler.toVO(post);
            vo.setCoverImageUrl(coverImageUrl);
            // 填充作者信息
            enrichPostWithAuthorInfo(vo, post);
            return vo;
        }

        // 返回包含完整内容的 VO
        PostVO vo = PostAssembler.toVOWithContent(post, detail.getContent());
        vo.setCoverImageUrl(coverImageUrl);
        // 填充作者信息
        enrichPostWithAuthorInfo(vo, post);
        return vo;
    }

    /**
     * 获取用户的文章列表
     *
     * @param userId 用户ID
     * @param status 状态
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    @Transactional(readOnly = true)
    public List<PostBriefVO> getUserPosts(Long userId, PostStatus status, int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findByOwnerId(userId, status, offset, size);
        return posts.stream()
                .map(post -> {
                    PostBriefVO vo = PostAssembler.toBriefVO(post);
                    // 获取封面图 URL
                    if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
                        vo.setCoverImageUrl(getFileUrl(post.getCoverImageId()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取已发布文章列表（分页）
     *
     * @param page 页码
     * @param size 每页大小
     * @return 文章列表
     */
    @Transactional(readOnly = true)
    public List<PostBriefVO> getPublishedPosts(int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findPublished(offset, size);
        return posts.stream()
                .map(post -> {
                    PostBriefVO vo = PostAssembler.toBriefVO(post);
                    // 获取封面图 URL
                    if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
                        vo.setCoverImageUrl(getFileUrl(post.getCoverImageId()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取已发布文章列表（游标分页）
     *
     * @param cursor 游标（上一页最后一条的发布时间）
     * @param size 每页大小
     * @return 文章列表
     */
    @Transactional(readOnly = true)
    public List<PostBriefVO> getPublishedPostsCursor(LocalDateTime cursor, int size) {
        List<Post> posts = postRepository.findPublishedCursor(cursor, size);
        return posts.stream()
                .map(post -> {
                    PostBriefVO vo = PostAssembler.toBriefVO(post);
                    // 获取封面图 URL
                    if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
                        vo.setCoverImageUrl(getFileUrl(post.getCoverImageId()));
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取已发布文章列表（混合分页）
     * 
     * 分页策略：
     * - 页码 ≤ 阈值（默认5）：使用 Offset 分页，支持跳页
     * - 页码 > 阈值：自动切换为 Cursor 分页，性能更好
     *
     * @param request 混合分页请求
     * @return 混合分页结果
     */
    @Transactional(readOnly = true)
    public HybridPageResult<PostBriefVO> getPublishedPostsHybrid(HybridPageRequest request) {
        int size = Math.min(request.getSize(), 100); // 限制最大每页大小

        // Cursor 分页模式
        if (request.isCursorMode()) {
            return queryPublishedPostsCursor(request.getCursor(), size);
        }

        // Offset 分页模式
        int page = request.getPage() != null ? request.getPage() : 1;
        if (page < 1) {
            page = 1;
        }

        // 检查是否超过混合分页阈值
        if (request.exceedsHybridThreshold()) {
            // 超过阈值，仍然支持 Offset 但建议切换到 Cursor
            return queryPublishedPostsOffsetWithSuggestion(page, size);
        }

        // 正常 Offset 分页
        return queryPublishedPostsOffset(page, size);
    }

    /**
     * Offset 分页查询已发布文章
     */
    private HybridPageResult<PostBriefVO> queryPublishedPostsOffset(int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findPublished(offset, size);
        long total = postRepository.countPublished();

        List<PostBriefVO> voList = enrichPostsWithAuthorInfo(posts);

        return HybridPageResult.ofOffset(voList, page, size, total);
    }

    /**
     * Offset 分页查询已发布文章（建议切换到 Cursor 模式）
     */
    private HybridPageResult<PostBriefVO> queryPublishedPostsOffsetWithSuggestion(int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postRepository.findPublished(offset, size);
        long total = postRepository.countPublished();

        List<PostBriefVO> voList = enrichPostsWithAuthorInfo(posts);

        return HybridPageResult.ofOffsetWithSuggestion(voList, page, size, total);
    }

    /**
     * Cursor 分页查询已发布文章
     */
    private HybridPageResult<PostBriefVO> queryPublishedPostsCursor(String cursor, int size) {
        LocalDateTime cursorTime = decodeCursor(cursor);
        
        // 多查一条用于判断是否有更多数据
        List<Post> posts = postRepository.findPublishedCursor(cursorTime, size + 1);

        boolean hasMore = posts.size() > size;
        String nextCursor = null;

        if (hasMore) {
            posts = posts.subList(0, size);
        }

        if (!posts.isEmpty()) {
            Post lastPost = posts.get(posts.size() - 1);
            nextCursor = encodeCursor(lastPost.getPublishedAt(), lastPost.getId());
        }

        List<PostBriefVO> voList = enrichPostsWithAuthorInfo(posts);

        return HybridPageResult.ofCursor(voList, nextCursor, hasMore, size);
    }

    /**
     * 编码游标
     * 格式：Base64(publishedAt|postId)
     */
    private String encodeCursor(LocalDateTime publishedAt, Long postId) {
        if (publishedAt == null) {
            return null;
        }
        String cursorData = publishedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "|" + postId;
        return Base64.getUrlEncoder().encodeToString(cursorData.getBytes());
    }

    /**
     * 解码游标
     */
    private LocalDateTime decodeCursor(String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = decoded.split("\\|");
            return LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception e) {
            log.warn("Invalid cursor format: {}", cursor, e);
            return null;
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 获取文章并检查所有权
     */
    private Post getPostAndCheckOwnership(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        if (!post.isOwnedBy(userId)) {
            throw new ForbiddenException("无权操作此文章");
        }

        return post;
    }

    /**
     * 计算 RocketMQ 延迟级别
     * 
     * RocketMQ 延迟级别：1s 5s 10s 30s 1m 2m 3m 4m 5m 6m 7m 8m 9m 10m 20m 30m 1h 2h
     * 对应级别：        1  2  3   4   5  6  7  8  9  10 11 12 13 14  15  16  17 18
     */
    private int calculateDelayLevel(LocalDateTime scheduledAt) {
        long delaySeconds = java.time.Duration.between(LocalDateTime.now(), scheduledAt).getSeconds();
        
        if (delaySeconds <= 1) return 1;      // 1s
        if (delaySeconds <= 5) return 2;      // 5s
        if (delaySeconds <= 10) return 3;     // 10s
        if (delaySeconds <= 30) return 4;     // 30s
        if (delaySeconds <= 60) return 5;     // 1m
        if (delaySeconds <= 120) return 6;    // 2m
        if (delaySeconds <= 180) return 7;    // 3m
        if (delaySeconds <= 240) return 8;    // 4m
        if (delaySeconds <= 300) return 9;    // 5m
        if (delaySeconds <= 360) return 10;   // 6m
        if (delaySeconds <= 420) return 11;   // 7m
        if (delaySeconds <= 480) return 12;   // 8m
        if (delaySeconds <= 540) return 13;   // 9m
        if (delaySeconds <= 600) return 14;   // 10m
        if (delaySeconds <= 1200) return 15;  // 20m
        if (delaySeconds <= 1800) return 16;  // 30m
        if (delaySeconds <= 3600) return 17;  // 1h
        return 18;                             // 2h (max)
    }

    /**
     * 批量获取文章信息
     *
     * @param postIds 文章ID集合
     * @return 文章ID到文章DTO的映射
     */
    @Transactional(readOnly = true)
    public Map<Long, PostDTO> batchGetPosts(Set<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return new HashMap<>();
        }
        
        Map<Long, Post> posts = postRepository.findByIds(new java.util.ArrayList<>(postIds));
        Map<Long, PostDTO> result = new HashMap<>();
        
        for (Map.Entry<Long, Post> entry : posts.entrySet()) {
            Post post = entry.getValue();
            if (!post.isDeleted()) {
                com.zhicore.api.dto.post.PostDTO dto = new com.zhicore.api.dto.post.PostDTO();
                dto.setId(post.getId());
                dto.setTitle(post.getTitle());
                dto.setOwnerId(post.getOwnerId());
                dto.setStatus(post.getStatus().name());
                dto.setCreatedAt(post.getCreatedAt());
                dto.setPublishedAt(post.getPublishedAt());
                result.put(post.getId(), dto);
            }
        }
        
        return result;
    }

    /**
     * 获取文章内容（延迟加载）
     * 仅从 MongoDB 获取内容，不包含元数据
     * 用于前端按需加载文章内容，提升列表页性能
     *
     * @param postId 文章ID
     * @return 文章内容
     */
    @Transactional(readOnly = true)
    public PostContent getPostContent(Long postId) {
        return dualStorageManager.getPostContent(postId);
    }

    // ==================== 草稿管理方法 ====================

    /**
     * 保存草稿
     * 支持自动保存和手动保存，每个用户每篇文章只保留一份草稿（Upsert模式）
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @param request 保存草稿请求
     */
    public void saveDraft(Long postId, Long userId, SaveDraftRequest request) {
        // 验证文章存在且用户有权限
        Post post = getPostAndCheckOwnership(postId, userId);
        
        // 调用 DraftManager 保存草稿
        draftManager.saveDraft(
            postId,
            userId,
            request.getContent(),
            request.getIsAutoSave() != null ? request.getIsAutoSave() : false
        );
        
        log.info("Draft saved: postId={}, userId={}, isAutoSave={}", 
            postId, userId, request.getIsAutoSave());
    }

    /**
     * 获取草稿
     * 查询指定文章的最新草稿
     *
     * @param postId 文章ID
     * @param userId 用户ID
     * @return 草稿视图对象
     */
    @Transactional(readOnly = true)
    public DraftVO getDraft(Long postId, Long userId) {
        // 验证文章存在且用户有权限
        Post post = getPostAndCheckOwnership(postId, userId);
        
        // 调用 DraftManager 获取草稿
        java.util.Optional<PostDraft> draftOpt =
            draftManager.getLatestDraft(postId, userId);
        
        if (draftOpt.isEmpty()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "草稿不存在");
        }
        
        PostDraft draft = draftOpt.get();
        
        // 转换为 VO
        return DraftVO.builder()
            .id(draft.getId())
            .postId(draft.getPostId())
            .userId(draft.getUserId())
            .content(draft.getContent())
            .contentType(draft.getContentType())
            .savedAt(draft.getSavedAt())
            .deviceId(draft.getDeviceId())
            .isAutoSave(draft.getIsAutoSave())
            .wordCount(draft.getWordCount())
            .build();
    }

    /**
     * 获取用户所有草稿
     * 按保存时间倒序返回用户的所有草稿
     *
     * @param userId 用户ID
     * @return 草稿列表
     */
    @Transactional(readOnly = true)
    public List<DraftVO> getUserDrafts(Long userId) {
        // 调用 DraftManager 获取用户所有草稿
        List<PostDraft> drafts =
            draftManager.getUserDrafts(userId);
        
        // 转换为 VO 列表
        return drafts.stream()
            .map(draft -> DraftVO.builder()
                .id(draft.getId())
                .postId(draft.getPostId())
                .userId(draft.getUserId())
                .content(draft.getContent())
                .contentType(draft.getContentType())
                .savedAt(draft.getSavedAt())
                .deviceId(draft.getDeviceId())
                .isAutoSave(draft.getIsAutoSave())
                .wordCount(draft.getWordCount())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 删除草稿
     * 当用户主动删除草稿或发布文章时调用
     *
     * @param postId 文章ID
     * @param userId 用户ID
     */
    public void deleteDraft(Long postId, Long userId) {
        // 验证文章存在且用户有权限
        Post post = getPostAndCheckOwnership(postId, userId);
        
        // 调用 DraftManager 删除草稿
        draftManager.deleteDraft(postId, userId);
        
        log.info("Draft deleted: postId={}, userId={}", postId, userId);
    }

    // ==================== 标签管理方法 ====================

    /**
     * 获取文章的标签列表
     *
     * @param postId 文章ID
     * @return 标签列表
     */
    @Transactional(readOnly = true)
    public List<TagDTO> getPostTags(Long postId) {
        // 验证文章存在
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND, "文章不存在"));

        if (post.isDeleted()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "文章已删除");
        }

        // 查询文章的标签ID列表
        List<Long> tagIds = postTagRepository.findTagIdsByPostId(postId);
        
        if (tagIds.isEmpty()) {
            return Collections.emptyList();
        }

        // 批量查询标签
        List<Tag> tags = tagRepository.findByIdIn(tagIds);

        // 转换为 DTO
        return tags.stream()
                .map(tag -> TagDTO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .slug(tag.getSlug())
                        .description(tag.getDescription())
                        .createdAt(tag.getCreatedAt())
                        .updatedAt(tag.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 获取文件 URL
     * 
     * 直接调用 ZhiCore-upload 服务获取文件 URL
     * 缓存逻辑由 file-service 内部实现
     * 
     * @param fileId 文件ID
     * @return 文件访问URL，如果获取失败返回 null
     */
    private String getFileUrl(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return null;
        }

        try {
            ApiResponse<String> response = ZhiCoreUploadClient.getFileUrl(fileId);
            if (response != null && response.isSuccess() && response.getData() != null) {
                return response.getData();
            } else {
                log.warn("Failed to get file URL from ZhiCore-upload: fileId={}, response={}",
                    fileId, response);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to get file URL: fileId={}", fileId, e);
            return null;
        }
    }

    /**
     * 填充单个文章的作者信息
     * 使用文章表中的冗余字段（ownerName），避免调用 user-service
     * 
     * @param vo 文章视图对象
     * @param post 文章领域对象
     */
    private void enrichPostWithAuthorInfo(PostVO vo, Post post) {
        if (vo == null || post == null) {
            return;
        }

        // 使用文章表中的冗余字段
        vo.setOwnerName(post.getOwnerName());
        log.debug("使用缓存的作者信息: postId={}, ownerName={}", post.getId(), post.getOwnerName());

        // 如果有 avatarId，调用 file-service 转换为 URL
        if (post.getOwnerAvatarId() != null && !post.getOwnerAvatarId().isEmpty()) {
            try {
                String avatarUrl = getFileUrl(post.getOwnerAvatarId());
                vo.setOwnerAvatar(avatarUrl);
            } catch (Exception e) {
                log.error("Failed to get avatar URL for fileId: {}", post.getOwnerAvatarId(), e);
                vo.setOwnerAvatar(null);
            }
        } else {
            vo.setOwnerAvatar(null);
        }
    }

    /**
     * 批量填充文章的作者信息
     * 使用文章表中的冗余字段（ownerName），避免调用 user-service
     * 
     * @param posts 文章列表
     * @return 填充了作者信息的 PostBriefVO 列表
     */
    private List<PostBriefVO> enrichPostsWithAuthorInfo(List<Post> posts) {
        if (posts == null || posts.isEmpty()) {
            return Collections.emptyList();
        }

        log.debug("使用缓存的作者信息填充 {} 篇文章", posts.size());

        // 组装 VO 并填充作者信息
        return posts.stream()
                .map(post -> {
                    PostBriefVO vo = PostAssembler.toBriefVO(post);
                    
                    // 获取封面图 URL
                    if (post.getCoverImageId() != null && !post.getCoverImageId().isEmpty()) {
                        vo.setCoverImageUrl(getFileUrl(post.getCoverImageId()));
                    }
                    
                    // 使用文章表中的冗余字段
                    vo.setOwnerName(post.getOwnerName());
                    
                    // 如果有 avatarId，调用 file-service 转换为 URL
                    if (post.getOwnerAvatarId() != null && !post.getOwnerAvatarId().isEmpty()) {
                        try {
                            String avatarUrl = getFileUrl(post.getOwnerAvatarId());
                            vo.setOwnerAvatar(avatarUrl);
                        } catch (Exception e) {
                            log.error("Failed to get avatar URL for fileId: {}", post.getOwnerAvatarId(), e);
                            vo.setOwnerAvatar(null);
                        }
                    } else {
                        vo.setOwnerAvatar(null);
                    }
                    
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 验证 fileId 格式（UUIDv7）
     * 
     * UUIDv7 格式：8-4-4-4-12 字符，第13位必须为7
     * 
     * @param fileId 文件ID
     * @throws IllegalArgumentException 如果格式无效
     */
    private void validateFileId(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return; // 允许为空
        }
        
        // UUIDv7 格式验证：8-4-4-4-12，第13位为7
        String uuidv7Pattern = "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";
        if (!fileId.matches(uuidv7Pattern)) {
            throw new IllegalArgumentException("无效的 fileId 格式: " + fileId);
        }
    }

    // ==================== 作者信息填充方法 ====================

    /**
     * 填充作者信息到文章对象
     * 
     * 从 user-service 获取作者信息并填充到 Post 对象中
     * 如果获取失败，使用默认值并安排补偿任务
     * 
     * @param post 文章对象
     * @param userId 用户ID（作者ID）
     */
    private void fillAuthorInfo(Post post, Long userId) {
        try {
            // 调用 user-service 获取用户信息
            ApiResponse<List<UserSimpleDTO>> response =
                userServiceClient.getUsersSimple(Collections.singletonList(userId));
            
            if (response != null && response.isSuccess() && response.getData() != null && !response.getData().isEmpty()) {
                // 成功获取用户信息
                UserSimpleDTO userInfo = response.getData().get(0);
                post.setOwnerInfo(userInfo.getNickname(), userInfo.getAvatarId(),
                    userInfo.getProfileVersion() != null ? userInfo.getProfileVersion() : 0L);
                
                log.info("作者信息填充成功: postId={}, userId={}, ownerName={}, profileVersion={}", 
                    post.getId(), userId, userInfo.getNickname(), userInfo.getProfileVersion());
            } else {
                // user-service 返回失败，使用默认值并安排补偿
                log.warn("获取用户信息失败，使用默认值并安排补偿: postId={}, userId={}, response={}", 
                    post.getId(), userId, response);
                setDefaultAuthorInfo(post);
                scheduleAuthorInfoCompensation(post.getId(), userId);
            }
        } catch (Exception e) {
            // 调用 user-service 异常，使用默认值并安排补偿
            log.error("调用 user-service 异常，使用默认值并安排补偿: postId={}, userId={}", 
                post.getId(), userId, e);
            setDefaultAuthorInfo(post);
            scheduleAuthorInfoCompensation(post.getId(), userId);
        }
    }

    /**
     * 设置默认作者信息
     * 
     * 当 user-service 不可用时使用默认值
     * 
     * @param post 文章对象
     */
    private void setDefaultAuthorInfo(Post post) {
        post.setOwnerInfo(PostConstants.UNKNOWN_AUTHOR_NAME, null, PostConstants.DEFAULT_PROFILE_VERSION);
        
        log.info("设置默认作者信息: postId={}",PostConstants.DEFAULT_PROFILE_VERSION);
        
        log.info("设置默认作者信息: postId={}, ownerName={}", 
            post.getId(), PostConstants.UNKNOWN_AUTHOR_NAME);
    }

    /**
     * 安排作者信息补偿任务
     * 
     * 发送延迟消息，1分钟后重试获取作者信息并更新文章
     * 
     * @param postId 文章ID
     * @param userId 用户ID（作者ID）
     */
    private void scheduleAuthorInfoCompensation(Long postId, Long userId) {
        try {
            // 创建补偿事件
            AuthorInfoCompensationEvent compensationEvent =
                new AuthorInfoCompensationEvent(
                    postId,
                    userId,
                    LocalDateTime.now()
                );
            
            // 发送延迟消息（延迟级别 5 = 1 分钟）
            eventPublisher.publishDelayed(compensationEvent, 5);
            
            log.info("作者信息补偿任务已安排: postId={}, userId={}, delayLevel=5 (1分钟)", 
                postId, userId);
        } catch (Exception e) {
            // 补偿任务发送失败，记录错误但不影响主流程
            log.error("安排作者信息补偿任务失败: postId={}, userId={}", postId, userId, e);
        }
    }
}

