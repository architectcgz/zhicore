package com.zhicore.common.scheduling;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Spring 定时任务线程池配置。
 *
 * <p>用于覆盖 Spring 默认单线程调度器，避免多个 {@code @Scheduled} 任务互相阻塞。</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.scheduling")
public class SchedulingProperties {

    /** 调度线程池大小。 */
    @Min(1)
    @Max(64)
    private int poolSize = 4;

    /** 调度线程名前缀，便于日志与线程栈排查。 */
    private String threadNamePrefix = "scheduled-";

    /** 应用关闭时是否等待已开始的定时任务执行完成。 */
    private boolean waitForTasksToCompleteOnShutdown = false;

    /** 应用关闭时等待定时任务收敛的最长秒数。 */
    @Min(0)
    @Max(300)
    private int awaitTerminationSeconds = 0;
}
