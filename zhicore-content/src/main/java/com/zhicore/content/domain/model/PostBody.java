package com.zhicore.content.domain.model;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 文章内容值对象
 * 
 * 存储在 MongoDB 中，与 Post 聚合分离。
 * 不可变对象，确保内容的完整性。
 * 使用 ContentType 枚举提供类型安全。
 * 
 * @author ZhiCore Team
 */
@Getter
public final class PostBody {
    
    /**
     * 文章ID（值对象，关联到 Post 聚合）
     */
    private final PostId postId;
    
    /**
     * 文章内容（Markdown 或 HTML）
     */
    private final String content;
    
    /**
     * 内容类型（使用枚举）
     */
    private final ContentType contentType;
    
    /**
     * 创建时间
     */
    private final LocalDateTime createdAt;
    
    /**
     * 更新时间
     */
    private final LocalDateTime updatedAt;
    
    /**
     * 构造函数（创建新内容）
     * 
     * @param postId 文章ID（值对象）
     * @param content 文章内容
     * @param contentType 内容类型（枚举）
     */
    public PostBody(PostId postId, String content, ContentType contentType) {
        this.postId = postId;
        this.content = content;
        this.contentType = contentType != null ? contentType : ContentType.MARKDOWN;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * 构造函数（从持久化恢复）
     * 
     * @param postId 文章ID（值对象）
     * @param content 文章内容
     * @param contentType 内容类型（枚举）
     * @param createdAt 创建时间
     * @param updatedAt 更新时间
     */
    public PostBody(PostId postId, String content, ContentType contentType, 
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.postId = postId;
        this.content = content;
        this.contentType = contentType != null ? contentType : ContentType.MARKDOWN;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    /**
     * 工厂方法（创建新内容）
     * 
     * @param postId 文章ID（值对象）
     * @param content 文章内容
     * @param contentType 内容类型（枚举）
     * @return PostBody 实例
     */
    public static PostBody create(PostId postId, String content, ContentType contentType) {
        return new PostBody(postId, content, contentType);
    }
    
    /**
     * 更新内容（返回新实例）
     * 
     * @param newContent 新内容
     * @return 新的 PostBody 实例
     */
    public PostBody updateContent(String newContent) {
        return new PostBody(this.postId, newContent, this.contentType, 
                           this.createdAt, LocalDateTime.now());
    }
    
    /**
     * 获取内容类型字符串值（用于向后兼容）
     * 
     * @return 内容类型的字符串值
     */
    public String getContentTypeValue() {
        return contentType.getValue();
    }
}
