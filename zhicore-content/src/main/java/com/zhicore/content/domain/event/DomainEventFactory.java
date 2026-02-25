package com.zhicore.content.domain.event;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * 领域事件工厂
 * <p>
 * 提供领域事件创建所需的通用功能：
 * - 生成事件ID（无连字符的UUID）
 * - 获取当前时间（UTC）
 * <p>
 * 注入 Clock 接口以支持测试时的时间控制
 */
@Component
public class DomainEventFactory {

    private final Clock clock;

    /**
     * 构造函数
     *
     * @param clock 时钟接口，用于获取当前时间
     */
    public DomainEventFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * 生成事件ID
     * <p>
     * 使用UUID生成唯一标识符，并移除连字符以减少存储空间
     *
     * @return 无连字符的UUID字符串
     */
    public String generateEventId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 获取当前时间
     * <p>
     * 返回UTC时间，确保时间的一致性和可测试性
     *
     * @return 当前时间的Instant对象
     */
    public Instant now() {
        return clock.instant();
    }
}
