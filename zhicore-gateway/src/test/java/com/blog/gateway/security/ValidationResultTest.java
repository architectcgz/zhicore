package com.zhicore.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationResult 单元测试
 * 测试验证结果数据模型的创建、序列化和字段访问
 */
class ValidationResultTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSuccessResult() {
        // 创建成功的验证结果
        ValidationResult result = ValidationResult.builder()
                .userId("user123")
                .userName("testuser")
                .roles("ROLE_USER")
                .build();

        // 验证字段
        assertEquals("user123", result.getUserId());
        assertEquals("testuser", result.getUserName());
        assertEquals("ROLE_USER", result.getRoles());
    }

    @Test
    void testFailureResult() {
        // 创建失败的验证结果（空对象表示失败）
        ValidationResult result = ValidationResult.builder().build();

        // 验证字段
        assertNull(result.getUserId());
        assertNull(result.getUserName());
        assertNull(result.getRoles());
    }

    @Test
    void testJsonSerialization() throws Exception {
        // 创建验证结果
        ValidationResult original = ValidationResult.builder()
                .userId("user456")
                .userName("john")
                .roles("ROLE_ADMIN")
                .build();

        // 序列化为 JSON
        String json = objectMapper.writeValueAsString(original);

        // 反序列化
        ValidationResult deserialized = objectMapper.readValue(json, ValidationResult.class);

        // 验证反序列化结果
        assertEquals(original.getUserId(), deserialized.getUserId());
        assertEquals(original.getUserName(), deserialized.getUserName());
        assertEquals(original.getRoles(), deserialized.getRoles());
    }

    @Test
    void testFailureResultSerialization() throws Exception {
        // 创建失败结果（空对象）
        ValidationResult original = ValidationResult.builder().build();

        // 序列化为 JSON
        String json = objectMapper.writeValueAsString(original);

        // 反序列化
        ValidationResult deserialized = objectMapper.readValue(json, ValidationResult.class);

        // 验证反序列化结果
        assertNull(deserialized.getUserId());
    }

    @Test
    void testNullValues() {
        // 测试 null 值处理
        ValidationResult result = ValidationResult.builder()
                .userId(null)
                .userName(null)
                .roles(null)
                .build();

        assertNull(result.getUserId());
        assertNull(result.getUserName());
        assertNull(result.getRoles());
    }

    @Test
    void testEmptyStrings() {
        // 测试空字符串
        ValidationResult result = ValidationResult.builder()
                .userId("")
                .userName("")
                .roles("")
                .build();

        assertEquals("", result.getUserId());
        assertEquals("", result.getUserName());
        assertEquals("", result.getRoles());
    }
}
