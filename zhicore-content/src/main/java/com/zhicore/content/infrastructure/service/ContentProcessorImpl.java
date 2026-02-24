package com.zhicore.content.infrastructure.service;

import com.zhicore.content.domain.service.ContentProcessor;
import com.zhicore.content.domain.valueobject.ContentBlock;
import com.zhicore.content.domain.valueobject.MediaResource;
import com.zhicore.content.infrastructure.config.ContentProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 内容处理器实现
 * 提供富文本内容的处理功能
 *
 * @author ZhiCore Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentProcessorImpl implements ContentProcessor {

    private final ObjectMapper objectMapper;
    private final ContentProperties contentProperties;

    // 支持的内容块类型
    private static final List<String> SUPPORTED_BLOCK_TYPES = List.of(
            "text", "image", "video", "code", "chart", "quote", "list", "table"
    );

    // 支持的媒体类型
    private static final List<String> SUPPORTED_MEDIA_TYPES = List.of(
            "image", "video", "audio"
    );

    // 图片URL匹配正则
    private static final Pattern IMAGE_PATTERN = Pattern.compile(
            "!\\[([^\\]]*)\\]\\(([^)]+)\\)|<img[^>]+src=[\"']([^\"']+)[\"']"
    );

    // 视频URL匹配正则
    private static final Pattern VIDEO_PATTERN = Pattern.compile(
            "<video[^>]+src=[\"']([^\"']+)[\"']|<source[^>]+src=[\"']([^\"']+)[\"']"
    );

    @Override
    public List<ContentBlock> processContentBlocks(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return new ArrayList<>();
        }

        List<ContentBlock> processedBlocks = new ArrayList<>();
        
        for (int i = 0; i < blocks.size(); i++) {
            ContentBlock block = blocks.get(i);
            
            // 验证块类型
            if (block.getType() == null || !SUPPORTED_BLOCK_TYPES.contains(block.getType())) {
                log.warn("Unsupported block type: {}, skipping", block.getType());
                continue;
            }

            // 确保order字段正确
            if (block.getOrder() == null) {
                block.setOrder(i);
            }

            // 验证内容不为空
            if (block.getContent() == null || block.getContent().trim().isEmpty()) {
                log.warn("Empty content in block type: {}, skipping", block.getType());
                continue;
            }

            processedBlocks.add(block);
        }

        return processedBlocks;
    }

    @Override
    public List<MediaResource> extractMediaMetadata(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<MediaResource> mediaList = new ArrayList<>();

        // 提取图片
        Matcher imageMatcher = IMAGE_PATTERN.matcher(content);
        while (imageMatcher.find()) {
            String url = imageMatcher.group(2) != null ? imageMatcher.group(2) : imageMatcher.group(3);
            if (url != null && !url.trim().isEmpty()) {
                MediaResource media = MediaResource.builder()
                        .type("image")
                        .url(url.trim())
                        .build();
                mediaList.add(media);
            }
        }

        // 提取视频
        Matcher videoMatcher = VIDEO_PATTERN.matcher(content);
        while (videoMatcher.find()) {
            String url = videoMatcher.group(1) != null ? videoMatcher.group(1) : videoMatcher.group(2);
            if (url != null && !url.trim().isEmpty()) {
                MediaResource media = MediaResource.builder()
                        .type("video")
                        .url(url.trim())
                        .build();
                mediaList.add(media);
            }
        }

        return mediaList;
    }

    @Override
    public int calculateWordCount(String content) {
        if (content == null || content.trim().isEmpty()) {
            return 0;
        }

        // 移除HTML标签
        String textOnly = content.replaceAll("<[^>]+>", "");
        
        // 移除Markdown语法
        textOnly = textOnly.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", ""); // 图片
        textOnly = textOnly.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1"); // 链接
        textOnly = textOnly.replaceAll("[#*`_~\\-]", ""); // Markdown符号
        
        // 移除多余空白
        textOnly = textOnly.replaceAll("\\s+", " ").trim();

        // 统计字数
        // 对于中文，每个字符算一个字
        // 对于英文，按空格分隔的单词计数
        int chineseCount = 0;
        int englishCount = 0;

        for (char c : textOnly.toCharArray()) {
            if (isChinese(c)) {
                chineseCount++;
            }
        }

        // 统计英文单词（按空格分隔）
        String[] words = textOnly.split("\\s+");
        for (String word : words) {
            if (!word.isEmpty() && !isChinese(word.charAt(0))) {
                englishCount++;
            }
        }

        return chineseCount + englishCount;
    }

    @Override
    public int calculateReadingTime(int wordCount) {
        if (wordCount <= 0) {
            return 1; // 最少1分钟
        }

        // 计算阅读时间，向上取整
        int minutes = (int) Math.ceil((double) wordCount / contentProperties.getWordsPerMinute());
        
        return Math.max(1, minutes); // 最少1分钟
    }

    @Override
    public String serializeContentBlocks(List<ContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize content blocks", e);
            throw new RuntimeException("Failed to serialize content blocks", e);
        }
    }

    @Override
    public List<ContentBlock> deserializeContentBlocks(String json) {
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return new ArrayList<>();
        }

        try {
            return objectMapper.readValue(json, new TypeReference<List<ContentBlock>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize content blocks: {}", json, e);
            throw new RuntimeException("Failed to deserialize content blocks", e);
        }
    }

    /**
     * 判断字符是否为中文
     */
    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }
}
