package com.zhicore.admin;

import com.zhicore.admin.infrastructure.feign.IdGeneratorClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Admin 启动配置稳定性测试")
class AdminApplicationStabilityTest {

    @Test
    @DisplayName("启动类应该使用全小写包路径进行组件与 Mapper 扫描")
    void shouldUseLowercasePackagesForComponentAndMapperScan() {
        SpringBootApplication application = AdminApplication.class.getAnnotation(SpringBootApplication.class);
        MapperScan mapperScan = AdminApplication.class.getAnnotation(MapperScan.class);
        EnableFeignClients enableFeignClients = AdminApplication.class.getAnnotation(EnableFeignClients.class);

        assertArrayEquals(new String[]{"com.zhicore.admin", "com.zhicore.common", "com.zhicore.api"},
                application.scanBasePackages());
        assertArrayEquals(new String[]{"com.zhicore.admin.infrastructure.repository.mapper"}, mapperScan.value());
        assertTrue(Arrays.asList(enableFeignClients.clients()).contains(IdGeneratorClient.class));
    }

    @Test
    @DisplayName("ID 生成器客户端默认地址应该指向容器内服务名")
    void shouldDefaultIdGeneratorUrlToContainerServiceName() {
        FeignClient feignClient = IdGeneratorClient.class.getAnnotation(FeignClient.class);

        assertEquals("${zhicore.id-generator.server-url:${ID_GENERATOR_SERVER_URL:http://zhicore-id-generator:8088}}",
                feignClient.url());
    }
}
