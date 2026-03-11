package com.zhicore.gateway.application.port.store;

import com.zhicore.gateway.application.model.ValidationResult;

import java.util.Optional;

/**
 * 封装 token 验证结果缓存，避免验证器直接依赖具体缓存实现。
 */
public interface TokenValidationStore {

    Optional<ValidationResult> get(String token);

    void put(String token, ValidationResult result);

    void invalidate(String token);

    CacheStats getStats();
}
