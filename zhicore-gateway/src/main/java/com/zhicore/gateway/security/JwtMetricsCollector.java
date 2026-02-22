package com.zhicore.gateway.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * JWT 验证指标收集器
 * 
 * 收集的指标：
 * - jwt.validation.success - 验证成功次数
 * - jwt.validation.failure - 验证失败次数（带 error tag）
 * - jwt.validation.expired - Token 过期次数
 * - jwt.cache.hit - 缓存命中次数
 * - jwt.cache.miss - 缓存未命中次数
 * - jwt.validation.time - 验证耗时
 * 
 * @author ZhiCore Team
 */
@Slf4j
@Component
public class JwtMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter successCounter;
    private final Counter expiredCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final Timer validationTimer;
    
    public JwtMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.successCounter = Counter.builder("jwt.validation.success")
                .description("Number of successful JWT validations")
                .register(meterRegistry);
        
        this.expiredCounter = Counter.builder("jwt.validation.expired")
                .description("Number of expired JWT tokens")
                .register(meterRegistry);
        
        this.cacheHitCounter = Counter.builder("jwt.cache.hit")
                .description("Number of JWT cache hits")
                .register(meterRegistry);
        
        this.cacheMissCounter = Counter.builder("jwt.cache.miss")
                .description("Number of JWT cache misses")
                .register(meterRegistry);
        
        this.validationTimer = Timer.builder("jwt.validation.time")
                .description("JWT validation time")
                .register(meterRegistry);
        
        log.info("JwtMetricsCollector initialized - metrics registered");
    }
    
    /**
     * 记录验证成功
     */
    public void recordSuccess() {
        successCounter.increment();
    }
    
    /**
     * 记录验证失败
     * 
     * @param error 失败原因（异常类名）
     */
    public void recordFailure(String error) {
        Counter.builder("jwt.validation.failure")
                .description("Number of failed JWT validations")
                .tag("error", error)
                .register(meterRegistry)
                .increment();
        log.warn("JWT validation failure recorded - reason: {}", error);
    }
    
    /**
     * 记录 Token 过期
     */
    public void recordExpired() {
        expiredCounter.increment();
    }
    
    /**
     * 记录缓存命中
     */
    public void recordCacheHit() {
        cacheHitCounter.increment();
    }
    
    /**
     * 记录缓存未命中
     */
    public void recordCacheMiss() {
        cacheMissCounter.increment();
    }
    
    /**
     * 记录验证耗时
     * 
     * @param nanos 耗时（纳秒）
     */
    public void recordValidationTime(long nanos) {
        validationTimer.record(nanos, TimeUnit.NANOSECONDS);
    }
}
