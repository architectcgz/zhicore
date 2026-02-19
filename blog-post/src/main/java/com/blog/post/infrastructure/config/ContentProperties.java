package com.blog.post.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 内容处理配置属性
 * 
 * 配置文章内容处理相关参数，包括阅读速度计算等
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "blog.post.content")
public class ContentProperties {
    
    /**
     * 每分钟阅读字数
     * 
     * 用于计算文章预估阅读时间
     * 中文平均阅读速度：200-250 字/分钟，默认取中间值 225
     * 取值范围：100-500
     */
    @Min(value = 100, message = "每分钟阅读字数必须至少为 100")
    @Max(value = 500, message = "每分钟阅读字数不能超过 500")
    private int wordsPerMinute = 225;
}
