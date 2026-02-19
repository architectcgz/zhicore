package com.blog.post.infrastructure.util;

import com.blog.post.domain.service.ContentProcessor;
import com.blog.post.infrastructure.mongodb.document.PostContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 内容增强器
 * 自动填充内容的元数据字段（字数、阅读时间、媒体资源等）
 *
 * @author Blog Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ContentEnricher {

    private final ContentProcessor contentProcessor;

    /**
     * 增强内容对象
     * 自动计算并填充字数、阅读时间、媒体资源等字段
     *
     * @param content 内容对象
     * @return 增强后的内容对象
     */
    public PostContent enrich(PostContent content) {
        if (content == null) {
            return null;
        }

        try {
            // 处理内容块
            if (content.getBlocks() != null && !content.getBlocks().isEmpty()) {
                content.setBlocks(contentProcessor.processContentBlocks(content.getBlocks()));
            }

            // 提取媒体资源元数据（如果未手动设置）
            if (content.getMedia() == null || content.getMedia().isEmpty()) {
                String contentText = getContentText(content);
                if (contentText != null && !contentText.isEmpty()) {
                    content.setMedia(contentProcessor.extractMediaMetadata(contentText));
                }
            }

            // 计算字数（如果未手动设置）
            if (content.getWordCount() == null || content.getWordCount() == 0) {
                String contentText = getContentText(content);
                if (contentText != null && !contentText.isEmpty()) {
                    int wordCount = contentProcessor.calculateWordCount(contentText);
                    content.setWordCount(wordCount);
                    
                    // 计算阅读时间
                    content.setReadingTime(contentProcessor.calculateReadingTime(wordCount));
                }
            } else if (content.getReadingTime() == null || content.getReadingTime() == 0) {
                // 如果字数已设置但阅读时间未设置，计算阅读时间
                content.setReadingTime(contentProcessor.calculateReadingTime(content.getWordCount()));
            }

            log.debug("Content enriched: postId={}, wordCount={}, readingTime={}, mediaCount={}",
                    content.getPostId(), content.getWordCount(), content.getReadingTime(),
                    content.getMedia() != null ? content.getMedia().size() : 0);

        } catch (Exception e) {
            log.error("Failed to enrich content for postId: {}", content.getPostId(), e);
            // 不抛出异常，允许保存继续进行
        }

        return content;
    }

    /**
     * 获取用于分析的内容文本
     * 优先使用text字段，其次raw字段，最后html字段
     */
    private String getContentText(PostContent content) {
        if (content.getText() != null && !content.getText().isEmpty()) {
            return content.getText();
        }
        if (content.getRaw() != null && !content.getRaw().isEmpty()) {
            return content.getRaw();
        }
        if (content.getHtml() != null && !content.getHtml().isEmpty()) {
            return content.getHtml();
        }
        return "";
    }
}
