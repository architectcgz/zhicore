package com.blog.post.infrastructure.service;

import com.blog.post.domain.model.Tag;
import com.blog.post.domain.repository.TagRepository;
import com.blog.post.domain.service.TagDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tag 领域服务实现
 *
 * @author Blog Team
 * @refactored 2026-02-19 从 domain.service.impl 移动到 infrastructure.service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagDomainServiceImpl implements TagDomainService {

    private final TagRepository tagRepository;

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
     */
    @Override
    public String normalizeToSlug(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("标签名称不能为空");
        }

        // Step 1: Trim
        String result = name.trim();

        // Step 2: 中文转拼音
        result = convertChineseToPinyin(result);

        // Step 3: 转换为小写
        result = result.toLowerCase();

        // Step 4: 将所有空白字符统一替换为 `-`
        result = result.replaceAll("\\s+", "-");

        // Step 5: 合并连续的 `-` 为单个 `-`
        result = result.replaceAll("-+", "-");

        // Step 6: 移除首尾的 `-`
        result = result.replaceAll("^-+|-+$", "");

        // Step 7: 过滤非法字符，仅保留 `[a-z0-9-]`
        result = result.replaceAll("[^a-z0-9-]", "");

        // 验证结果不为空
        if (!StringUtils.hasText(result)) {
            throw new IllegalArgumentException("标签名称规范化后为空，无法创建标签");
        }

        return result;
    }

    /**
     * 验证标签名称合法性
     */
    @Override
    public void validateTagName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("标签名称不能为空");
        }

        String trimmedName = name.trim();

        if (trimmedName.length() > 50) {
            throw new IllegalArgumentException("标签名称不能超过50字符");
        }

        // 验证名称只能包含中文、英文、数字、空格、连字符和下划线
        if (!trimmedName.matches("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-_]+$")) {
            throw new IllegalArgumentException("标签名称只能包含中文、英文、数字、空格、连字符和下划线");
        }
    }

    /**
     * 查找或创建标签（幂等操作）
     */
    @Override
    public Tag findOrCreate(String name) {
        // 1. 验证标签名称
        validateTagName(name);

        // 2. 规范化为 slug
        String slug = normalizeToSlug(name);

        // 3. 根据 slug 查询是否已存在
        Optional<Tag> existing = tagRepository.findBySlug(slug);
        if (existing.isPresent()) {
            log.debug("Tag already exists with slug: {}", slug);
            return existing.get();
        }

        // 4. 不存在则创建
        try {
            // 生成 ID（这里需要 ID 生成器，暂时使用占位符）
            // TODO: 集成 ID 生成器
            Long id = generateId();
            Tag tag = Tag.create(id, name.trim(), slug);
            Tag saved = tagRepository.save(tag);
            log.info("Created new tag: id={}, name={}, slug={}", saved.getId(), saved.getName(), saved.getSlug());
            return saved;
        } catch (DataIntegrityViolationException e) {
            // 5. 处理并发冲突（唯一索引冲突时重新查询）
            log.warn("Concurrent tag creation detected for slug: {}, retrying query", slug);
            return tagRepository.findBySlug(slug)
                    .orElseThrow(() -> new IllegalStateException("创建标签失败，且无法查询到已存在的标签: " + slug));
        }
    }

    /**
     * 批量查找或创建标签
     */
    @Override
    public List<Tag> findOrCreateBatch(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }

        // 去重并验证
        List<String> uniqueNames = names.stream()
                .distinct()
                .collect(Collectors.toList());

        // 逐个处理（简单实现，可以优化为批量查询）
        List<Tag> tags = new ArrayList<>();
        for (String name : uniqueNames) {
            try {
                Tag tag = findOrCreate(name);
                tags.add(tag);
            } catch (Exception e) {
                log.error("Failed to find or create tag: {}", name, e);
                // 继续处理其他标签
            }
        }

        return tags;
    }

    /**
     * 中文转拼音
     * 
     * @param text 原始文本
     * @return 拼音文本
     */
    private String convertChineseToPinyin(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);

        StringBuilder result = new StringBuilder();
        char[] chars = text.toCharArray();

        for (char c : chars) {
            if (Character.toString(c).matches("[\\u4e00-\\u9fa5]")) {
                // 中文字符，转换为拼音
                try {
                    String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, format);
                    if (pinyinArray != null && pinyinArray.length > 0) {
                        result.append(pinyinArray[0]);
                    } else {
                        result.append(c);
                    }
                } catch (BadHanyuPinyinOutputFormatCombination e) {
                    log.warn("Failed to convert Chinese character to pinyin: {}", c, e);
                    result.append(c);
                }
            } else {
                // 非中文字符，直接保留
                result.append(c);
            }
        }

        return result.toString();
    }

    /**
     * 生成 ID（临时实现）
     * TODO: 集成 Leaf ID 生成器
     */
    private Long generateId() {
        // 临时使用时间戳 + 随机数
        return System.currentTimeMillis() * 1000 + (long) (Math.random() * 1000);
    }
}
