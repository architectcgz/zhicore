package com.blog.message.integration;

import com.blog.api.client.IdGeneratorFeignClient;
import com.blog.common.result.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ID生成器集成测试
 * 
 * 验证blog-message服务能够正确使用IdGeneratorFeignClient生成ID
 */
@SpringBootTest
@ActiveProfiles("test")
class IdGeneratorIntegrationTest {

    @Autowired(required = false)
    private IdGeneratorFeignClient idGeneratorFeignClient;

    @Test
    void shouldInjectIdGeneratorFeignClient() {
        // 验证IdGeneratorFeignClient能够被正确注入
        assertThat(idGeneratorFeignClient)
                .as("IdGeneratorFeignClient should be injected")
                .isNotNull();
    }

    @Test
    void shouldHaveCorrectFeignClientConfiguration() {
        // 验证Feign客户端配置正确
        assertThat(idGeneratorFeignClient)
                .as("IdGeneratorFeignClient should be properly configured")
                .isNotNull();
        
        // 验证Feign客户端的方法存在
        assertThat(idGeneratorFeignClient.getClass().getMethods())
                .as("IdGeneratorFeignClient should have generateSnowflakeId method")
                .anyMatch(method -> method.getName().equals("generateSnowflakeId"));
    }
}
