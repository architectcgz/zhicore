package com.zhicore.idgenerator.property;

import com.platform.idgen.client.IdGeneratorClient;
import com.zhicore.idgenerator.service.impl.IdGeneratorServiceImpl;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * ID生成服务属性测试
 * 
 * 使用 jqwik 进行属性测试，验证核心正确性属性
 * 每个测试运行 100 次迭代
 */
class IdGeneratorPropertyTest {

    /**
     * Property 1: ID Uniqueness
     * 
     * Feature: ZhiCore-id-generator-service
     * Property 1: For any sequence of ID generation requests (Snowflake or Segment),
     * all generated IDs should be unique within the system.
     * 
     * Validates: Requirements 1.1, 2.1, 3.1
     */
    @Property(tries = 100)
    @Label("Feature: ZhiCore-id-generator-service, Property 1: ID Uniqueness")
    void generatedIdsShouldBeUnique(@ForAll @IntRange(min = 1, max = 100) int count) {
        // Given: Mock IdGeneratorClient that returns unique IDs
        IdGeneratorClient mockClient = Mockito.mock(IdGeneratorClient.class);
        
        // Generate unique IDs for each call
        List<Long> allGeneratedIds = new ArrayList<>();
        long baseId = System.currentTimeMillis();
        
        // Mock single ID generation
        when(mockClient.nextSnowflakeId()).thenAnswer(invocation -> {
            long id = baseId + allGeneratedIds.size();
            allGeneratedIds.add(id);
            return id;
        });
        
        // Mock batch ID generation
        when(mockClient.nextSnowflakeIds(anyInt())).thenAnswer(invocation -> {
            int batchCount = invocation.getArgument(0);
            List<Long> batchIds = new ArrayList<>();
            for (int i = 0; i < batchCount; i++) {
                long id = baseId + allGeneratedIds.size();
                allGeneratedIds.add(id);
                batchIds.add(id);
            }
            return batchIds;
        });
        
        // Mock segment ID generation
        when(mockClient.nextSegmentId(anyString())).thenAnswer(invocation -> {
            long id = baseId + allGeneratedIds.size();
            allGeneratedIds.add(id);
            return id;
        });
        
        IdGeneratorServiceImpl service =
                new IdGeneratorServiceImpl(mockClient);
        
        // When: Generate multiple IDs
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(service.generateSnowflakeId());
        }
        
        // Then: All IDs should be unique
        Set<Long> uniqueIds = new HashSet<>(ids);
        assertThat(uniqueIds).hasSize(ids.size());
    }

    /**
     * Property 2: Batch Count Consistency
     * 
     * Feature: ZhiCore-id-generator-service
     * Property 2: For any valid batch count request (1 ≤ count ≤ 1000),
     * the returned list size should equal the requested count.
     * 
     * Validates: Requirements 2.2
     */
    @Property(tries = 100)
    @Label("Feature: ZhiCore-id-generator-service, Property 2: Batch Count Consistency")
    void batchGenerationShouldReturnCorrectCount(@ForAll @IntRange(min = 1, max = 1000) int count) {
        // Given: Mock IdGeneratorClient that returns correct number of IDs
        IdGeneratorClient mockClient = Mockito.mock(IdGeneratorClient.class);
        
        when(mockClient.nextSnowflakeIds(anyInt())).thenAnswer(invocation -> {
            int requestedCount = invocation.getArgument(0);
            return LongStream.range(0, requestedCount)
                    .boxed()
                    .collect(Collectors.toList());
        });
        
        IdGeneratorServiceImpl service =
                new IdGeneratorServiceImpl(mockClient);
        
        // When: Generate batch IDs
        List<Long> ids = service.generateBatchSnowflakeIds(count);
        
        // Then: Returned list size should equal requested count
        assertThat(ids).hasSize(count);
    }

    /**
     * Property 3: Invalid Input Rejection
     * 
     * Feature: ZhiCore-id-generator-service
     * Property 3: For any invalid input (count ≤ 0, count > 1000, empty bizTag),
     * the system should reject the request with an appropriate error response.
     * 
     * Validates: Requirements 2.3, 2.4, 3.2
     */
    @Property(tries = 100)
    @Label("Feature: ZhiCore-id-generator-service, Property 3: Invalid Input Rejection - Zero or Negative Count")
    void shouldRejectInvalidBatchCount_ZeroOrNegative(@ForAll @IntRange(min = -100, max = 0) int invalidCount) {
        // Given: Service with mock client
        IdGeneratorClient mockClient = Mockito.mock(IdGeneratorClient.class);
        IdGeneratorServiceImpl service =
                new IdGeneratorServiceImpl(mockClient);
        
        // When & Then: Should reject invalid count
        assertThatThrownBy(() -> service.generateBatchSnowflakeIds(invalidCount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("生成数量必须在1-1000之间");
    }

    @Property(tries = 100)
    @Label("Feature: ZhiCore-id-generator-service, Property 3: Invalid Input Rejection - Count Too Large")
    void shouldRejectInvalidBatchCount_TooLarge(@ForAll @IntRange(min = 1001, max = 10000) int invalidCount) {
        // Given: Service with mock client
        IdGeneratorClient mockClient = Mockito.mock(IdGeneratorClient.class);
        IdGeneratorServiceImpl service =
                new IdGeneratorServiceImpl(mockClient);
        
        // When & Then: Should reject invalid count
        assertThatThrownBy(() -> service.generateBatchSnowflakeIds(invalidCount))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("生成数量必须在1-1000之间");
    }

    @Property(tries = 100)
    @Label("Feature: ZhiCore-id-generator-service, Property 3: Invalid Input Rejection - Empty BizTag")
    void shouldRejectEmptyBizTag(@ForAll("emptyOrBlankStrings") String invalidBizTag) {
        // Given: Service with mock client
        IdGeneratorClient mockClient = Mockito.mock(IdGeneratorClient.class);
        IdGeneratorServiceImpl service =
                new IdGeneratorServiceImpl(mockClient);
        
        // When & Then: Should reject empty or blank bizTag
        assertThatThrownBy(() -> service.generateSegmentId(invalidBizTag))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("业务标签不能为空");
    }

    /**
     * Arbitrary provider for empty or blank strings
     */
    @Provide
    Arbitrary<String> emptyOrBlankStrings() {
        return Arbitraries.oneOf(
                Arbitraries.just(""),
                Arbitraries.just(" "),
                Arbitraries.just("  "),
                Arbitraries.just("   "),
                Arbitraries.just("\t"),
                Arbitraries.just("\n"),
                Arbitraries.just(" \t\n ")
        );
    }

    /**
     * Additional Property: Batch IDs Should Be Unique
     * 
     * Verifies that all IDs in a batch are unique
     */
    @Property(tries = 100)
    @Label("Feature: ZhiCore-id-generator-service, Additional Property: Batch IDs Uniqueness")
    void batchIdsShouldBeUnique(@ForAll @IntRange(min = 2, max = 100) int count) {
        // Given: Mock IdGeneratorClient that returns unique IDs
        IdGeneratorClient mockClient = Mockito.mock(IdGeneratorClient.class);
        
        when(mockClient.nextSnowflakeIds(anyInt())).thenAnswer(invocation -> {
            int requestedCount = invocation.getArgument(0);
            long baseId = System.currentTimeMillis();
            return LongStream.range(0, requestedCount)
                    .map(i -> baseId + i)
                    .boxed()
                    .collect(Collectors.toList());
        });
        
        IdGeneratorServiceImpl service =
                new IdGeneratorServiceImpl(mockClient);
        
        // When: Generate batch IDs
        List<Long> ids = service.generateBatchSnowflakeIds(count);
        
        // Then: All IDs in the batch should be unique
        Set<Long> uniqueIds = new HashSet<>(ids);
        assertThat(uniqueIds).hasSize(count);
    }

    /**
     * Additional Property: Valid Count Range Acceptance
     * 
     * Verifies that all valid counts (1-1000) are accepted
     */
    @Property(tries = 100)
    @Label("Feature: ZhiCore-id-generator-service, Additional Property: Valid Count Acceptance")
    void shouldAcceptValidBatchCounts(@ForAll @IntRange(min = 1, max = 1000) int validCount) {
        // Given: Mock IdGeneratorClient
        IdGeneratorClient mockClient = Mockito.mock(IdGeneratorClient.class);
        
        when(mockClient.nextSnowflakeIds(anyInt())).thenAnswer(invocation -> {
            int requestedCount = invocation.getArgument(0);
            return LongStream.range(0, requestedCount)
                    .boxed()
                    .collect(Collectors.toList());
        });
        
        IdGeneratorServiceImpl service =
                new IdGeneratorServiceImpl(mockClient);
        
        // When & Then: Should accept valid count without throwing exception
        List<Long> ids = service.generateBatchSnowflakeIds(validCount);
        assertThat(ids).hasSize(validCount);
    }
}
