package com.zhicore.content.domain.valueobject;

import lombok.Value;

/**
 * 草稿内容值对象
 * 封装草稿文本内容，提供验证和业务规则
 * 
 * 注意：不进行 trim() 等内容修改，保持用户输入的原始格式
 * 
 * @author ZhiCore Team
 */
@Value
public class DraftContent {
    private static final int MAX_LENGTH = 1_000_000; // 最大字符数（约 1MB UTF-8 文本）
    
    String text;
    
    private DraftContent(String text) {
        this.text = text;
    }
    
    /**
     * 创建草稿内容
     * @param text 原始文本（保持原样，不做 trim 等修改）
     * @return DraftContent 实例
     * @throws IllegalArgumentException 如果内容不符合规则
     */
    public static DraftContent of(String text) {
        validate(text);
        return new DraftContent(text);  // 不做任何修改，保持原样
    }
    
    /**
     * 创建空草稿内容
     */
    public static DraftContent empty() {
        return new DraftContent("");
    }
    
    private static void validate(String text) {
        if (text == null) {
            throw new IllegalArgumentException("草稿内容不能为 null");
        }
        
        if (text.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                String.format("草稿内容超过最大长度限制 %d 字符", MAX_LENGTH)
            );
        }
    }
    
    /**
     * 检查是否为空内容（空字符串或全空白）
     */
    public boolean isEmpty() {
        return text.isBlank();
    }
    
    /**
     * 获取内容长度（字符数）
     */
    public int length() {
        return text.length();
    }
}
