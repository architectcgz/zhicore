package com.zhicore.content.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异步事件处理配置测试（R15）
 *
 * 覆盖：线程池配置默认值、线程名前缀
 */
@DisplayName("异步事件配置测试")
class AsyncEventConfigTest {

    @Nested
    @DisplayName("线程池配置默认值")
    class DefaultConfigTests {

        @Test
        @DisplayName("默认 corePoolSize = 8")
        void defaultCorePoolSizeShouldBe8() {
            AsyncEventProperties props = new AsyncEventProperties();
            assertThat(props.getCorePoolSize()).isEqualTo(8);
        }

        @Test
        @DisplayName("默认 maxPoolSize = 16")
        void defaultMaxPoolSizeShouldBe16() {
            AsyncEventProperties props = new AsyncEventProperties();
            assertThat(props.getMaxPoolSize()).isEqualTo(16);
        }

        @Test
        @DisplayName("默认 queueCapacity = 1000")
        void defaultQueueCapacityShouldBe1000() {
            AsyncEventProperties props = new AsyncEventProperties();
            assertThat(props.getQueueCapacity()).isEqualTo(1000);
        }

        @Test
        @DisplayName("默认线程名前缀为 async-event-")
        void defaultThreadNamePrefixShouldBeAsyncEvent() {
            AsyncEventProperties props = new AsyncEventProperties();
            assertThat(props.getThreadNamePrefix()).isEqualTo("async-event-");
        }
    }
}
