package com.blog.common.util;

import com.blog.common.exception.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * QueryParamValidator 单元测试
 *
 * @author Blog Team
 */
class QueryParamValidatorTest {

    @Test
    void validatePage_withValidPage_shouldReturnSamePage() {
        // Given
        int page = 5;

        // When
        int result = QueryParamValidator.validatePage(page);

        // Then
        assertThat(result).isEqualTo(5);
    }

    @Test
    void validatePage_withZeroPage_shouldReturnDefaultPage() {
        // Given
        int page = 0;

        // When
        int result = QueryParamValidator.validatePage(page);

        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    void validatePage_withNegativePage_shouldReturnDefaultPage() {
        // Given
        int page = -1;

        // When
        int result = QueryParamValidator.validatePage(page);

        // Then
        assertThat(result).isEqualTo(1);
    }

    @Test
    void validateSize_withValidSize_shouldReturnSameSize() {
        // Given
        int size = 50;

        // When
        int result = QueryParamValidator.validateSize(size);

        // Then
        assertThat(result).isEqualTo(50);
    }

    @Test
    void validateSize_withZeroSize_shouldReturnDefaultSize() {
        // Given
        int size = 0;

        // When
        int result = QueryParamValidator.validateSize(size);

        // Then
        assertThat(result).isEqualTo(20);
    }

    @Test
    void validateSize_withNegativeSize_shouldReturnDefaultSize() {
        // Given
        int size = -10;

        // When
        int result = QueryParamValidator.validateSize(size);

        // Then
        assertThat(result).isEqualTo(20);
    }

    @Test
    void validateSize_withOversizedSize_shouldReturnMaxSize() {
        // Given
        int size = 200;

        // When
        int result = QueryParamValidator.validateSize(size);

        // Then
        assertThat(result).isEqualTo(100);
    }

    @Test
    void validateKeyword_withValidKeyword_shouldReturnTrimmedKeyword() {
        // Given
        String keyword = "  test keyword  ";

        // When
        String result = QueryParamValidator.validateKeyword(keyword);

        // Then
        assertThat(result).isEqualTo("test keyword");
    }

    @Test
    void validateKeyword_withNullKeyword_shouldReturnNull() {
        // Given
        String keyword = null;

        // When
        String result = QueryParamValidator.validateKeyword(keyword);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void validateKeyword_withEmptyKeyword_shouldReturnNull() {
        // Given
        String keyword = "";

        // When
        String result = QueryParamValidator.validateKeyword(keyword);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void validateKeyword_withWhitespaceKeyword_shouldReturnNull() {
        // Given
        String keyword = "   ";

        // When
        String result = QueryParamValidator.validateKeyword(keyword);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void validateKeyword_withOversizedKeyword_shouldThrowException() {
        // Given
        String keyword = "a".repeat(150);

        // When & Then
        assertThatThrownBy(() -> QueryParamValidator.validateKeyword(keyword))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("关键词长度不能超过100字符");
    }

    @Test
    void validateStatus_withValidStatus_shouldReturnUppercaseStatus() {
        // Given
        String status = "active";

        // When
        String result = QueryParamValidator.validateStatus(status);

        // Then
        assertThat(result).isEqualTo("ACTIVE");
    }

    @Test
    void validateStatus_withNullStatus_shouldReturnNull() {
        // Given
        String status = null;

        // When
        String result = QueryParamValidator.validateStatus(status);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void validateStatus_withEmptyStatus_shouldReturnNull() {
        // Given
        String status = "";

        // When
        String result = QueryParamValidator.validateStatus(status);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void validateStatus_withWhitespaceStatus_shouldReturnNull() {
        // Given
        String status = "   ";

        // When
        String result = QueryParamValidator.validateStatus(status);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void validateId_withValidId_shouldReturnTrimmedId() {
        // Given
        String id = "  123456  ";

        // When
        String result = QueryParamValidator.validateId(id);

        // Then
        assertThat(result).isEqualTo("123456");
    }

    @Test
    void validateId_withNullId_shouldReturnNull() {
        // Given
        String id = null;

        // When
        String result = QueryParamValidator.validateId(id);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void validateId_withEmptyId_shouldReturnNull() {
        // Given
        String id = "";

        // When
        String result = QueryParamValidator.validateId(id);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void calculateOffset_shouldReturnCorrectOffset() {
        // Given
        int page = 3;
        int size = 20;

        // When
        int result = QueryParamValidator.calculateOffset(page, size);

        // Then
        assertThat(result).isEqualTo(40);
    }

    @Test
    void calculateOffset_withFirstPage_shouldReturnZero() {
        // Given
        int page = 1;
        int size = 20;

        // When
        int result = QueryParamValidator.calculateOffset(page, size);

        // Then
        assertThat(result).isEqualTo(0);
    }
}
