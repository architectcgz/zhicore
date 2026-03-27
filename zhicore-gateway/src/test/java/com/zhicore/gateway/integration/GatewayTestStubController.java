package com.zhicore.gateway.integration;

import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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

    @GetMapping("/__test/public/user-detail")
    ResponseEntity<String> publicUserDetailEndpoint() {
        return ResponseEntity.ok("user-detail");
    }

    @PostMapping("/__test/public/auth/login")
    ResponseEntity<String> publicAuthLoginEndpoint(@RequestBody String body) {
        return ResponseEntity.ok("login-ok:" + body);
    }

    @GetMapping("/__test/public/comment-detail")
    ResponseEntity<String> publicCommentDetailEndpoint() {
        return ResponseEntity.ok("comment-detail");
    }

    @GetMapping("/__test/public/comment-list")
    ResponseEntity<String> publicCommentListEndpoint() {
        return ResponseEntity.ok("comment-list");
    }

    @GetMapping("/__test/public/comment-like-count")
    ResponseEntity<String> publicCommentLikeCountEndpoint() {
        return ResponseEntity.ok("comment-like-count");
    }

    @GetMapping("/__test/public/comment-replies-page")
    ResponseEntity<String> publicCommentRepliesPageEndpoint() {
        return ResponseEntity.ok("comment-replies-page");
    }

    @GetMapping("/__test/public/file-detail")
    ResponseEntity<String> publicFileDetailEndpoint(@RequestHeader("X-App-Id") String appId) {
        return ResponseEntity.ok("file-detail:" + appId);
    }

    @GetMapping(value = "/__test/ws/message/info", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> messageWebSocketInfoEndpoint() {
        return ResponseEntity.ok(Map.of("service", "message", "websocket", true));
    }

    @GetMapping(value = "/__test/ws/notification/info", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, Object>> notificationWebSocketInfoEndpoint() {
        return ResponseEntity.ok(Map.of("service", "notification", "websocket", true));
    }
}
