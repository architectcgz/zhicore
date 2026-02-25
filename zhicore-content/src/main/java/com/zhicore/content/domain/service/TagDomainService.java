package com.zhicore.content.domain.service;

import com.zhicore.content.domain.model.Tag;

import java.util.List;

/**
 * Tag 领域服务接口
 * 
 * 负责 Tag 相关的领域逻辑：
 * 1. Slug 规范化
 * 2. Tag 验证
 * 3. Tag 查找或创建（幂等操作）
 *
 * @author ZhiCore Team
 */
public interface TagDomainService {

    /**
     * 规范化标签名称为 slug
     * 
     * 规范化流程（7步）：
     * 1. 对原始名称进行 trim
     * 2. 中文转拼音（使用 pinyin4j 库）
     * 3. 转换为小写
     * 4. 将所有空白字符统一替换为 `-`
     * 5. 合并连续的 `-` 为单个 `-`
     * 6. 移除首尾的 `-`
     * 7. 过滤非法字符，仅保留 `[a-z0-9-]`
     * 
     * 示例：
     * - "Spring Boot" → "spring-boot"
     * - "  Java  " → "java"
     * - "C++" → "c"
     * - "数据库" → "shu-ju-ku"
     * - "PostgreSQL" → "postgresql"
     * - "   " → throw ValidationException
     * 
     * @param name 标签名称
     * @return 规范化后的 slug
     * @throws IllegalArgumentException 如果名称为空或规范化后为空
     */
    String normalizeToSlug(String name);

    /**
     * 验证标签名称合法性
     * 
     * 验证规则：
     * 1. 名称不能为空
     * 2. 名称长度不能超过 50 字符
     * 3. 名称只能包含中文、英文、数字、空格、连字符和下划线
     * 
     * @param name 标签名称
     * @throws IllegalArgumentException 如果名称不合法
     */
    void validateTagName(String name);

    /**
     * 查找或创建标签（幂等操作）
     * 
     * 流程：
     * 1. 验证标签名称
     * 2. 规范化为 slug
     * 3. 根据 slug 查询是否已存在
     * 4. 如果存在则返回，不存在则创建
     * 5. 处理并发冲突（唯一索引冲突时重新查询）
     * 
     * @param name 标签名称
     * @return Tag 实例
     */
    Tag findOrCreate(String name);

    /**
     * 批量查找或创建标签
     * 
     * @param names 标签名称列表
     * @return Tag 列表
     */
    List<Tag> findOrCreateBatch(List<String> names);
}
