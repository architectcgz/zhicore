package com.zhicore.content.infrastructure.util;

import com.zhicore.content.domain.service.ContentProcessor;
import com.zhicore.content.domain.valueobject.ContentBlock;
import com.zhicore.content.domain.valueobject.MediaResource;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostContent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 内容增强器
 * 自动填充内容的元数据字段（字数、阅读时间、媒体资源等）
 *
 * @author ZhiCore Team
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
                List<ContentBlock> blocks = content.getBlocks().stream()
                        .map(this::toDomainContentBlock)
                        .collect(Collectors.toList());
                List<ContentBlock> processed = contentProcessor.processContentBlocks(blocks);
                content.setBlocks(processed.stream()
                        .map(this::toDocumentContentBlock)
                        .collect(Collectors.toList()));
            }

            // 提取媒体资源元数据（如果未手动设置）
            if (content.getMedia() == null || content.getMedia().isEmpty()) {
                String contentText = getContentText(content);
                if (contentText != null && !contentText.isEmpty()) {
                    List<MediaResource> media = contentProcessor.extractMediaMetadata(contentText);
                    content.setMedia(media.stream()
                            .map(this::toDocumentMediaResource)
                            .collect(Collectors.toList()));
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

    private ContentBlock toDomainContentBlock(PostContent.ContentBlock block) {
        return ContentBlock.builder()
                .type(block.getType())
                .content(block.getContent())
                .props(block.getProps())
                .order(block.getOrder())
                .build();
    }

    private PostContent.ContentBlock toDocumentContentBlock(ContentBlock block) {
        return PostContent.ContentBlock.builder()
                .type(block.getType())
                .content(block.getContent())
                .props(block.getProps())
                .order(block.getOrder())
                .build();
    }

    private PostContent.MediaResource toDocumentMediaResource(MediaResource resource) {
        return PostContent.MediaResource.builder()
                .type(resource.getType())
                .url(resource.getUrl())
                .thumbnail(resource.getThumbnail())
                .size(resource.getSize())
                .metadata(resource.getMetadata())
                .build();
    }
}

