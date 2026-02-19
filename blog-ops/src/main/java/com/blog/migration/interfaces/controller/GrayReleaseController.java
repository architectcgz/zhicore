package com.blog.migration.interfaces.controller;

import com.blog.migration.infrastructure.gray.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 灰度发布控制器
 * 提供灰度发布管理的 REST API
 */
@Tag(name = "灰度发布管理", description = "灰度发布配置、流量控制、数据对账、回滚等相关接口")
@Slf4j
@RestController
@RequestMapping("/api/gray")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "gray.enabled", havingValue = "true")
public class GrayReleaseController {

    private final GrayRouter grayRouter;
    private final GrayRollbackService grayRollbackService;
    private final GrayDataReconciliationTask reconciliationTask;

    /**
     * 获取灰度配置
     */
    @Operation(summary = "获取灰度配置", description = "获取当前的灰度发布配置信息")
    @GetMapping("/config")
    public ResponseEntity<GrayConfig> getConfig() {
        GrayConfig config = grayRouter.getConfig();
        return ResponseEntity.ok(config);
    }

    /**
     * 更新灰度配置
     */
    @Operation(summary = "更新灰度配置", description = "更新灰度发布配置，包括流量比例、白名单等")
    @PutMapping("/config")
    public ResponseEntity<GrayConfig> updateConfig(
            @Parameter(description = "灰度配置信息", required = true)
            @RequestBody GrayConfig config) {
        log.info("更新灰度配置: trafficRatio={}%", config.getTrafficRatio());
        grayRouter.updateConfig(config);
        return ResponseEntity.ok(config);
    }

    /**
     * 检查用户是否在灰度中
     */
    @Operation(summary = "检查用户灰度状态", description = "检查指定用户是否被分配到灰度环境")
    @GetMapping("/check/{userId}")
    public ResponseEntity<Map<String, Object>> checkUser(
            @Parameter(description = "用户ID", required = true, example = "1001")
            @PathVariable String userId) {
        boolean isGray = grayRouter.shouldRouteToGray(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("isGray", isGray);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 执行回滚
     */
    @Operation(summary = "执行灰度回滚", description = "将灰度环境回滚到稳定版本")
    @PostMapping("/rollback")
    public ResponseEntity<GrayRollbackService.RollbackResult> rollback(
            @Parameter(description = "回滚原因", example = "手动触发")
            @RequestParam(defaultValue = "手动触发") String reason) {
        log.warn("收到灰度回滚请求, 原因: {}", reason);
        GrayRollbackService.RollbackResult result = grayRollbackService.rollback(reason);
        return ResponseEntity.ok(result);
    }

    /**
     * 推进灰度阶段
     */
    @Operation(summary = "推进灰度阶段", description = "将灰度发布推进到下一个阶段，增加流量比例")
    @PostMapping("/advance")
    public ResponseEntity<GrayRollbackService.AdvanceResult> advancePhase() {
        log.info("收到灰度阶段推进请求");
        GrayRollbackService.AdvanceResult result = grayRollbackService.advancePhase();
        return ResponseEntity.ok(result);
    }

    /**
     * 手动触发数据对账
     */
    @Operation(summary = "手动触发数据对账", description = "立即执行灰度环境与生产环境的数据对账")
    @PostMapping("/reconcile")
    public ResponseEntity<Map<String, Object>> triggerReconciliation() {
        log.info("手动触发数据对账");
        reconciliationTask.reconcile();
        
        GrayDataReconciliationTask.ReconciliationResult result = reconciliationTask.getLatestResult();
        
        Map<String, Object> response = new HashMap<>();
        response.put("triggered", true);
        response.put("result", result);
        
        return ResponseEntity.ok(response);
    }

    /**
     * 获取最新对账结果
     */
    @Operation(summary = "获取最新对账结果", description = "获取最近一次数据对账的结果")
    @GetMapping("/reconciliation/result")
    public ResponseEntity<GrayDataReconciliationTask.ReconciliationResult> getReconciliationResult() {
        GrayDataReconciliationTask.ReconciliationResult result = reconciliationTask.getLatestResult();
        return ResponseEntity.ok(result);
    }

    /**
     * 检查是否需要自动回滚
     */
    @Operation(summary = "检查自动回滚条件", description = "检查当前是否满足自动回滚的条件")
    @GetMapping("/check-rollback")
    public ResponseEntity<Map<String, Object>> checkAutoRollback() {
        boolean shouldRollback = grayRollbackService.shouldAutoRollback();
        
        Map<String, Object> result = new HashMap<>();
        result.put("shouldRollback", shouldRollback);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 清除用户灰度标记
     */
    @Operation(summary = "清除用户灰度标记", description = "清除指定用户的灰度环境标记")
    @DeleteMapping("/user-flags/{userId}")
    public ResponseEntity<Map<String, Object>> clearUserFlag(
            @Parameter(description = "用户ID", required = true, example = "1001")
            @PathVariable String userId) {
        grayRouter.clearUserGrayFlag(userId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("cleared", true);
        
        return ResponseEntity.ok(result);
    }

    /**
     * 清除所有用户灰度标记
     */
    @Operation(summary = "清除所有用户灰度标记", description = "清除所有用户的灰度环境标记")
    @DeleteMapping("/user-flags")
    public ResponseEntity<Map<String, Object>> clearAllUserFlags() {
        grayRouter.clearAllUserGrayFlags();
        
        Map<String, Object> result = new HashMap<>();
        result.put("cleared", true);
        
        return ResponseEntity.ok(result);
    }
}
