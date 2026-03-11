package com.zhicore.content.application.service;

import com.zhicore.common.result.PageResult;
import com.zhicore.content.application.assembler.TagAssembler;
import com.zhicore.content.application.dto.TagDTO;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 标签分页列表查询服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TagListQueryService {

    private final TagRepository tagRepository;
    private final TagAssembler tagAssembler;

    public PageResult<TagDTO> listTags(int page, int size) {
        log.debug("Listing tags: page={}, size={}", page, size);

        Pageable pageable = PageRequest.of(page, size);
        Page<Tag> tagPage = tagRepository.findAll(pageable);
        List<TagDTO> tagDTOs = tagPage.getContent().stream()
                .map(tagAssembler::toDTO)
                .collect(Collectors.toList());

        return PageResult.of(page, size, tagPage.getTotalElements(), tagDTOs);
    }
}
