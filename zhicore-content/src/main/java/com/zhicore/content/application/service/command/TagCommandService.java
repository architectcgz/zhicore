package com.zhicore.content.application.service.command;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.ServiceUnavailableException;
import com.zhicore.common.exception.ValidationException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.domain.service.TagDomainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 标签写服务。
 *
 * 负责标签的查找/创建与批量创建编排，领域规则由 {@link TagDomainService} 提供。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagCommandService {

    private final TagRepository tagRepository;
    private final IdGeneratorFeignClient idGeneratorFeignClient;
    private final TagDomainService tagDomainService;

    public Tag findOrCreate(String name) {
        tagDomainService.validateTagName(name);
        String normalizedName = name.trim();
        String slug = tagDomainService.normalizeToSlug(normalizedName);

        Optional<Tag> existing = tagRepository.findBySlug(slug);
        if (existing.isPresent()) {
            log.debug("Tag already exists with slug: {}", slug);
            return existing.get();
        }

        try {
            Long id = generateIdOrThrow();
            Tag saved = tagRepository.save(Tag.create(id, normalizedName, slug));
            log.info("Created new tag: id={}, name={}, slug={}", saved.getId(), saved.getName(), saved.getSlug());
            return saved;
        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent tag creation detected for slug: {}, retrying query", slug);
            return tagRepository.findBySlug(slug)
                    .orElseThrow(() -> new IllegalStateException("创建标签失败，且无法查询到已存在的标签: " + slug));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Tag> findOrCreateBatch(List<String> names) {
        if (names == null || names.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> uniqueNames = names.stream()
                .distinct()
                .toList();

        List<Tag> tags = new ArrayList<>();
        List<ValidationException.FieldError> errors = new ArrayList<>();

        for (String name : uniqueNames) {
            try {
                tags.add(findOrCreate(name));
            } catch (ServiceUnavailableException e) {
                throw e;
            } catch (IllegalArgumentException e) {
                errors.add(new ValidationException.FieldError(
                        "tags",
                        "标签【" + name + "】创建失败: " + e.getMessage()
                ));
            } catch (Exception e) {
                log.error("批量创建标签失败: name={}", name, e);
                errors.add(new ValidationException.FieldError(
                        "tags",
                        "标签【" + name + "】创建失败: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                ));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        return tags;
    }

    private Long generateIdOrThrow() {
        try {
            ApiResponse<Long> response = idGeneratorFeignClient.generateSnowflakeId();
            if (response == null || !response.isSuccess() || response.getData() == null || response.getData() <= 0) {
                log.error("ID 服务返回异常，无法生成标签ID: response={}", response);
                throw new ServiceUnavailableException("ID 服务不可用，无法创建标签，请稍后重试");
            }
            return response.getData();
        } catch (ServiceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用 ID 服务生成标签ID失败", e);
            throw new ServiceUnavailableException("ID 服务不可用，无法创建标签，请稍后重试", e);
        }
    }
}
