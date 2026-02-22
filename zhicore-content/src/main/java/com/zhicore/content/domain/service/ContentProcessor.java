package com.zhicore.content.domain.service;

import com.zhicore.content.infrastructure.mongodb.document.PostContent;

import java.util.List;

/**
 * 内容处理器接口
 * 负责处理富文本内容的序列化、反序列化、元数据提取等操作
 *
 * @author ZhiCore Team
 */
public interface ContentProcessor {

    /**
     * 处理内容块列表
     * 验证内容块的类型和结构，确保数据完整性
     *
     * @param blocks 内容块列表
     * @return 处理后的内容块列表
     */
    List<PostContent.ContentBlock> processContentBlocks(List<PostContent.ContentBlock> blocks);

    /**
     * 提取媒体资源元数据
     * 从内容中提取所有媒体资源的元数据信息
     *
     * @param content 文章内容
     * @return 媒体资源列表
     */
    List<PostContent.MediaResource> extractMediaMetadata(String content);

    /**
     * 计算字数
     * 统计文章的字数（不包括HTML标签和特殊字符）
     *
     * @param content 文章内容
     * @return 字数
     */
    int calculateWordCount(String content);

    /**
     * 计算阅读时间
     * 根据字数估算阅读时间（分钟）
     *
     * @param wordCount 字数
     * @return 阅读时间（分钟）
     */
    int calculateReadingTime(int wordCount);

    /**
     * 序列化内容块
     * 将内容块列表序列化为JSON字符串
     *
     * @param blocks 内容块列表
     * @return JSON字符串
     */
    String serializeContentBlocks(List<PostContent.ContentBlock> blocks);

    /**
     * 反序列化内容块
     * 将JSON字符串反序列化为内容块列表
     *
     * @param json JSON字符串
     * @return 内容块列表
     */
    List<PostContent.ContentBlock> deserializeContentBlocks(String json);
}
