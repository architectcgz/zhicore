package com.zhicore.content.interfaces.assembler;

import com.zhicore.content.application.command.commands.CreatePostCommand;
import com.zhicore.content.application.query.view.PostDetailView;
import com.zhicore.content.application.query.view.PostListItemView;
import com.zhicore.content.domain.model.*;
import com.zhicore.content.interfaces.dto.request.CreatePostRequest;
import com.zhicore.content.interfaces.dto.response.PostDetailResponse;
import com.zhicore.content.interfaces.dto.response.PostListItemResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文章 Assembler
 * 
 * 负责 DTO 和 Domain 对象之间的转换
 * - Request DTO → Command（Long 转值对象）
 * - View → Response DTO（值对象转 Long）
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostApiAssembler {
    
    /**
     * Request DTO 转 Command（Long 转值对象）
     * 
     * @param request 创建文章请求
     * @param postId 文章 ID（Long）
     * @param userId 用户 ID（Long）
     * @return 创建文章命令
     */
    public CreatePostCommand toCommand(CreatePostRequest request, Long postId, Long userId) {
        // 构建 OwnerSnapshot（使用默认值，实际应从用户服务获取）
        OwnerSnapshot ownerSnapshot = new OwnerSnapshot(
                UserId.of(userId),
                "Unknown",  // 默认名称，实际应从用户服务获取
                null,       // 默认头像
                1L          // 初始版本
        );
        
        // 构建 TagId 集合（String 转值对象）
        Set<TagId> tagIds = request.getTags() != null
                ? request.getTags().stream()
                        .map(TagId::new)
                        .collect(Collectors.toSet())
                : Collections.emptySet();
        
        // 构建 TopicId（Long 转值对象）
        TopicId topicId = request.getTopicId() != null
                ? TopicId.of(request.getTopicId())
                : null;
        
        return new CreatePostCommand(
                PostId.of(postId),
                UserId.of(userId),
                request.getTitle(),
                null,  // excerpt - 从内容自动生成
                request.getCoverImageId(),
                request.getContent(),
                ContentType.MARKDOWN,  // 默认使用 markdown
                ownerSnapshot,
                tagIds,
                topicId
        );
    }
    
    /**
     * View 转 Response DTO（值对象转 Long）
     * 
     * @param view 文章详情视图
     * @return 文章详情响应 DTO
     */
    public PostDetailResponse toResponse(PostDetailView view) {
        // 转换标签 ID（值对象转 Long）
        List<Long> tagIds = view.getTagIds() != null
                ? view.getTagIds().stream()
                        .map(TagId::getValue)
                        .collect(Collectors.toList())
                : Collections.emptyList();
        
        // 转换话题 ID（值对象转 Long）
        Long topicId = view.getTopicId() != null
                ? view.getTopicId().getValue()
                : null;
        
        return PostDetailResponse.builder()
                .id(view.getId().getValue())  // 值对象转 Long
                .title(view.getTitle())
                .excerpt(view.getExcerpt())
                .coverImage(view.getCoverImage())
                .content(view.getContent())
                .contentDegraded(view.isContentDegraded())
                .status(view.getStatus().name())
                .authorId(view.getOwnerSnapshot().getOwnerId().getValue())  // 值对象转 Long
                .authorName(view.getOwnerSnapshot().getName())
                .authorAvatar(view.getOwnerSnapshot().getAvatarId())
                .tagIds(tagIds)
                .topicId(topicId)
                .viewCount(view.getViewCount())
                .likeCount(view.getLikeCount())
                .commentCount(view.getCommentCount())
                .shareCount(view.getShareCount())
                .createdAt(view.getCreatedAt())
                .updatedAt(view.getUpdatedAt())
                .scheduledPublishAt(view.getScheduledPublishAt())
                .build();
    }
    
    /**
     * 列表视图转响应 DTO（值对象转 Long）
     * 
     * @param view 文章列表项视图
     * @return 文章列表项响应 DTO
     */
    public PostListItemResponse toListItemResponse(PostListItemView view) {
        return PostListItemResponse.builder()
                .id(view.getId().getValue())  // 值对象转 Long
                .title(view.getTitle())
                .excerpt(view.getExcerpt())
                .coverImage(view.getCoverImage())
                .authorId(view.getOwnerSnapshot().getOwnerId().getValue())  // 值对象转 Long
                .authorName(view.getOwnerSnapshot().getName())
                .authorAvatar(view.getOwnerSnapshot().getAvatarId())
                .viewCount(view.getViewCount())
                .likeCount(view.getLikeCount())
                .commentCount(view.getCommentCount())
                .shareCount(view.getShareCount())
                .createdAt(view.getCreatedAt())
                .build();
    }
    
    /**
     * 从请求字符串解析 ContentType
     * 
     * 将 API 层的字符串内容类型转换为领域层的 ContentType 枚举。
     * 如果传入无效的内容类型，抛出 IllegalArgumentException，
     * 由全局异常处理器捕获并返回 400 错误。
     * 
     * @param contentTypeStr 内容类型字符串（如 "markdown", "html", "rich"）
     * @return ContentType 枚举
     * @throws IllegalArgumentException 如果内容类型无效
     */
    public ContentType parseContentType(String contentTypeStr) {
        try {
            return ContentType.fromValue(contentTypeStr);
        } catch (IllegalArgumentException e) {
            // 重新抛出带友好消息的异常，包含 "内容类型" 关键字用于全局异常处理器识别
            log.warn("解析内容类型失败: {}", contentTypeStr);
            throw new IllegalArgumentException(
                "无效的内容类型: " + contentTypeStr + 
                ", 有效值: markdown, html, rich"
            );
        }
    }
}
