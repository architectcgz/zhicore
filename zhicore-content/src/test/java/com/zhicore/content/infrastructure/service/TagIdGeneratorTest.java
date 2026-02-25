package com.zhicore.content.infrastructure.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.common.exception.ServiceUnavailableException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import com.zhicore.content.domain.service.TagDomainService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 标签 ID 生成测试（R16）
 *
 * 覆盖：调用统一 ID 服务、ID 服务不可用返回 503、无本地降级
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("标签 ID 生成测试")
class TagIdGeneratorTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @InjectMocks
    private TagDomainServiceImpl tagDomainService;

    // ==================== R16 ID 服务调用测试 ====================

    @Nested
    @DisplayName("ID 服务调用")
    class IdServiceCallTests {

        @Test
        @DisplayName("创建标签时调用 IdGeneratorFeignClient")
        void findOrCreateShouldCallIdGenerator() {
            when(tagRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(idGeneratorFeignClient.generateSnowflakeId())
                    .thenReturn(ApiResponse.success(12345L));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

            tagDomainService.findOrCreate("测试标签");

            verify(idGeneratorFeignClient).generateSnowflakeId();
        }

        @Test
        @DisplayName("ID 服务返回 null 应抛出 ServiceUnavailableException")
        void nullResponseShouldThrow503() {
            when(tagRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(idGeneratorFeignClient.generateSnowflakeId()).thenReturn(null);

            assertThatThrownBy(() -> tagDomainService.findOrCreate("测试标签"))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        @Test
        @DisplayName("ID 服务抛出异常应包装为 ServiceUnavailableException")
        void exceptionShouldBeWrappedAs503() {
            when(tagRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(idGeneratorFeignClient.generateSnowflakeId())
                    .thenThrow(new RuntimeException("连接超时"));

            assertThatThrownBy(() -> tagDomainService.findOrCreate("测试标签"))
                    .isInstanceOf(ServiceUnavailableException.class);
        }

        @Test
        @DisplayName("已存在的标签不调用 ID 服务")
        void existingTagShouldNotCallIdGenerator() {
            Tag existing = Tag.create(1L, "已有标签", "yi-you-biao-qian");
            when(tagRepository.findBySlug(anyString())).thenReturn(Optional.of(existing));

            tagDomainService.findOrCreate("已有标签");

            verify(idGeneratorFeignClient, never()).generateSnowflakeId();
        }
    }
}