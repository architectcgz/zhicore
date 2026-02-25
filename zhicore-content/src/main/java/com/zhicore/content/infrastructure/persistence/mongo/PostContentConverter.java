package com.zhicore.content.infrastructure.persistence.mongo;

import com.zhicore.content.domain.model.ContentType;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostContentDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PostBody 与 PostContentDocument 转换器
 * 
 * 负责领域对象与持久化文档之间的转换，处理 ContentType 枚举与字符串的转换。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class PostContentConverter {
    
    /**
     * 领域对象转文档
     * 
     * @param body 文章内容领域对象
     * @return PostContentDocument 持久化文档
     */
    public PostContentDocument toDocument(PostBody body) {
        PostContentDocument document = new PostContentDocument();
        document.setPostId(body.getPostId().getValue());
        document.setContent(body.getContent());
        document.setContentType(body.getContentType().getValue()); // 枚举转字符串
        document.setCreatedAt(body.getCreatedAt());
        document.setUpdatedAt(body.getUpdatedAt());
        return document;
    }
    
    /**
     * 文档转领域对象
     * 
     * @param document 持久化文档
     * @return PostBody 文章内容领域对象
     */
    public PostBody toDomain(PostContentDocument document) {
        PostId postId = PostId.of(document.getPostId());
        
        // 字符串转枚举，使用默认值处理无效值
        ContentType contentType = ContentType.fromValueOrDefault(
            document.getContentType(), 
            ContentType.MARKDOWN
        );
        
        // 如果使用了默认值，记录警告
        if (!contentType.getValue().equals(document.getContentType())) {
            log.warn("文章 {} 的内容类型无效: {}, 使用默认值: {}", 
                    document.getPostId(), 
                    document.getContentType(), 
                    contentType.getValue());
        }
        
        return new PostBody(
            postId,
            document.getContent(),
            contentType,
            document.getCreatedAt(),
            document.getUpdatedAt()
        );
    }
}
