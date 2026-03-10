package com.zhicore.content.application.port.store;

import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;

import java.util.Optional;

/**
 * 文章内容存储端口接口
 * 
 * 定义文章内容在 MongoDB 中的存储操作契约，由基础设施层实现。
 * 内容与元数据分离存储，支持大文本存储和独立扩展。
 * 
 * @author ZhiCore Team
 */
public interface PostContentStore {
    
    /**
     * 保存文章内容
     * 
     * @param postId 文章ID（值对象）
     * @param body 文章内容
     */
    void saveContent(PostId postId, PostBody body);
    
    /**
     * 获取文章内容（降级读）
     * 
     * @param postId 文章ID（值对象）
     * @return 文章内容（可能为空；底层异常可按实现降级为空）
     */
    Optional<PostBody> getContent(PostId postId);

    /**
     * 严格加载文章内容
     *
     * @param postId 文章ID（值对象）
     * @return 文章内容（可能为空）
     * @throws RuntimeException 底层存储读取失败时抛出
     */
    Optional<PostBody> loadContent(PostId postId);
    
    /**
     * 删除文章内容
     * 
     * @param postId 文章ID（值对象）
     */
    void deleteContent(PostId postId);
}

