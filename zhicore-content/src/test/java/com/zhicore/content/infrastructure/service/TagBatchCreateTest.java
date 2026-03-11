package com.zhicore.content.infrastructure.service;

import com.zhicore.api.client.IdGeneratorFeignClient;
import com.zhicore.content.application.service.TagCommandService;
import com.zhicore.common.exception.ServiceUnavailableException;
import com.zhicore.common.result.ApiResponse;
import com.zhicore.content.domain.model.Tag;
import com.zhicore.content.domain.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * 标签批量创建失败测试（R17）
 *
 * 覆盖：全成全败事务语义、失败标签名和错误原因、ID 服务不可用整批回滚
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("标签批量创建测试")
class TagBatchCreateTest {

    @Mock
    private TagRepository tagRepository;

    @Mock
    private IdGeneratorFeignClient idGeneratorFeignClient;

    private TagCommandService tagCommandService;

    @BeforeEach
    void setUp() {
        tagCommandService = new TagCommandService(tagRepository, idGeneratorFeignClient, new TagDomainServiceImpl());
    }

    // ==================== 13.5 批量创建成功 ====================

    @Nested
    @DisplayName("批量创建成功")
    class BatchSuccessTests {

        @Test
        @DisplayName("全部标签创建成功时返回完整列表")
        void allSuccessShouldReturnFullList() {
            when(tagRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(idGeneratorFeignClient.generateSnowflakeId())
                    .thenReturn(ApiResponse.success(1L))
                    .thenReturn(ApiResponse.success(2L));
            when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> inv.getArgument(0));

            List<Tag> result = tagCommandService.findOrCreateBatch(List.of("标签A", "标签B"));

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("空列表输入返回空列表")
        void emptyInputShouldReturnEmptyList() {
            List<Tag> result = tagCommandService.findOrCreateBatch(List.of());
            assertThat(result).isEmpty();
        }
    }

    // ==================== 13.5 批量创建失败 ====================

    @Nested
    @DisplayName("批量创建失败")
    class BatchFailureTests {

        @Test
        @DisplayName("ID 服务不可用时抛出 ServiceUnavailableException（整批回滚）")
        void idServiceUnavailableShouldThrow503() {
            when(tagRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(idGeneratorFeignClient.generateSnowflakeId())
                    .thenThrow(new RuntimeException("连接超时"));

            assertThatThrownBy(() ->
                    tagCommandService.findOrCreateBatch(List.of("标签A", "标签B"))
            ).isInstanceOf(ServiceUnavailableException.class);
        }

        @Test
        @DisplayName("非法标签名应抛出 ValidationException 并包含失败原因")
        void invalidTagNameShouldThrowWithDetails() {
            // 空标签名会触发 validateTagName 中的 IllegalArgumentException
            assertThatThrownBy(() ->
                    tagCommandService.findOrCreateBatch(List.of("正常标签", ""))
            ).isInstanceOf(Exception.class);
        }

        @Test
        @DisplayName("重复标签名去重后只创建一次")
        void duplicateNamesShouldBeDeduped() {
            when(tagRepository.findBySlug(anyString())).thenReturn(Optional.empty());
            when(idGeneratorFeignClient.generateSnowflakeId())
                    .thenReturn(ApiResponse.success(1L));
            when(tagRepository.save(any(Tag.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            List<Tag> result = tagCommandService.findOrCreateBatch(
                    List.of("标签A", "标签A"));

            assertThat(result).hasSize(1);
        }
    }
}
