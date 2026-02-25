package com.zhicore.idgenerator.integration;

import com.zhicore.idgenerator.controller.IdGeneratorController;
import com.zhicore.idgenerator.service.IdGeneratorService;
import com.platform.idgen.client.IdGeneratorClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 应用上下文集成测试
 * 
 * 验证Spring应用上下文能够正确加载，所有必需的Bean都已注册
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("应用上下文集成测试")
class ApplicationContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    @DisplayName("应用上下文应该成功加载")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("应该注册IdGeneratorController Bean")
    void shouldRegisterIdGeneratorController() {
        assertThat(applicationContext.containsBean("idGeneratorController")).isTrue();
        
        IdGeneratorController controller = applicationContext.getBean(IdGeneratorController.class);
        assertThat(controller).isNotNull();
    }

    @Test
    @DisplayName("应该注册IdGeneratorService Bean")
    void shouldRegisterIdGeneratorService() {
        IdGeneratorService service = applicationContext.getBean(IdGeneratorService.class);
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("应该注册IdGeneratorClient Bean")
    void shouldRegisterIdGeneratorClient() {
        IdGeneratorClient client = applicationContext.getBean(IdGeneratorClient.class);
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("所有必需的Bean都应该正确注册")
    void shouldRegisterAllRequiredBeans() {
        // Verify all critical beans are present
        String[] requiredBeans = {
                "idGeneratorController",
                "idGeneratorServiceImpl"
        };

        for (String beanName : requiredBeans) {
            assertThat(applicationContext.containsBean(beanName))
                    .as("Bean '%s' should be registered", beanName)
                    .isTrue();
        }
    }
}
