package com.zhicore.gateway.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 为 Gateway 集成测试提供本地可转发的下游桩端点，
 * 避免依赖真实服务注册与下游实例。
 */
@RestController
class GatewayTestStubController {

    @GetMapping("/__test/protected")
    ResponseEntity<String> protectedEndpoint() {
        return ResponseEntity.ok("protected");
    }

    @GetMapping("/__test/public")
    ResponseEntity<String> publicEndpoint() {
        return ResponseEntity.ok("public");
    }
}
