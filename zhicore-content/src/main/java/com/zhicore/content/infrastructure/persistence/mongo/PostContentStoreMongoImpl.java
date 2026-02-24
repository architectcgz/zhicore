package com.zhicore.content.infrastructure.persistence.mongo;

import com.zhicore.content.application.port.store.PostContentStore;
import com.zhicore.content.domain.model.PostBody;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.infrastructure.persistence.mongo.document.PostContentDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MongoDB Post Content Store 实现
 * 
 * 实现 PostContentStore 端口接口，使用 Spring Data MongoDB 访问 MongoDB。
 * 负责文章内容的存储和查询，支持降级处理。
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class PostContentStoreMongoImpl implements PostContentStore {

    private final MongoTemplate mongoTemplate;
    private final PostContentConverter converter;

    @Override
    public void saveContent(PostId postId, PostBody body) {
        try {
            // 使用 converter 转换领域对象为文档
            PostContentDocument document = converter.toDocument(body);

            // 先尝试查找是否已存在
            Query query = Query.query(Criteria.where("postId").is(postId.getValue()));
            PostContentDocument existing = mongoTemplate.findOne(query, PostContentDocument.class);

            if (existing != null) {
                // 更新现有文档
                document.setId(existing.getId());
                document.setCreatedAt(existing.getCreatedAt());
                document.setUpdatedAt(LocalDateTime.now());
                mongoTemplate.save(document);
                log.info("更新文章内容成功: postId={}", postId.getValue());
            } else {
                // 插入新文档
                mongoTemplate.insert(document);
                log.info("保存文章内容成功: postId={}", postId.getValue());
            }

        } catch (Exception e) {
            log.error("保存文章内容失败: postId={}", postId.getValue(), e);
            throw new RuntimeException("保存文章内容到 MongoDB 失败", e);
        }
    }

    @Override
    public Optional<PostBody> getContent(PostId postId) {
        try {
            // 值对象转 String 用于查询
            Query query = Query.query(Criteria.where("postId").is(postId.getValue()));
            PostContentDocument document = mongoTemplate.findOne(query, PostContentDocument.class);

            if (document == null) {
                log.debug("文章内容不存在: postId={}", postId.getValue());
                return Optional.empty();
            }

            // 使用 converter 转换文档为领域对象
            PostBody body = converter.toDomain(document);

            log.debug("获取文章内容成功: postId={}", postId.getValue());
            return Optional.of(body);

        } catch (Exception e) {
            // 降级处理：连接失败时返回空，不抛出异常
            log.warn("获取文章内容失败（降级处理）: postId={}", postId.getValue(), e);
            return Optional.empty();
        }
    }

    @Override
    public void deleteContent(PostId postId) {
        try {
            // 值对象转 String
            Query query = Query.query(Criteria.where("postId").is(postId.getValue()));
            mongoTemplate.remove(query, PostContentDocument.class);
            log.info("删除文章内容成功: postId={}", postId.getValue());

        } catch (Exception e) {
            log.error("删除文章内容失败: postId={}", postId.getValue(), e);
            throw new RuntimeException("删除文章内容失败", e);
        }
    }

    /**
     * 检查 MongoDB 连接是否可用
     * 
     * 用于健康检查和降级判断。
     * 
     * @return true 如果连接可用
     */
    public boolean isAvailable() {
        try {
            mongoTemplate.getDb().getName();
            return true;
        } catch (Exception e) {
            log.warn("MongoDB 连接不可用", e);
            return false;
        }
    }
}
