package com.zhicore.common.util;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DateTimeUtilsContractTest {

    @Test
    void shouldNotExposeLegacyCompatibilityMethods() {
        Method[] methods = DateTimeUtils.class.getDeclaredMethods();

        assertFalse(hasMethod(methods, "nowLocal"),
                "DateTimeUtils 不应再暴露 nowLocal，统一使用 nowOffset");
        assertFalse(hasMethod(methods, "businessDateTime"),
                "DateTimeUtils 不应再暴露 businessDateTime，统一使用 businessOffsetDateTime");
        assertFalse(hasMethod(methods, "toOffsetDateTime", OffsetDateTime.class),
                "DateTimeUtils 不应再保留 OffsetDateTime -> OffsetDateTime 的兼容重载");
    }

    private boolean hasMethod(Method[] methods, String name, Class<?>... parameterTypes) {
        return Arrays.stream(methods)
                .anyMatch(method -> method.getName().equals(name)
                        && Arrays.equals(method.getParameterTypes(), parameterTypes));
    }
}
