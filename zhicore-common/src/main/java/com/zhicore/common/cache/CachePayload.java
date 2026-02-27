package com.zhicore.common.cache;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 缓存序列化载荷（R11）
 *
 * 编码约定：
 * - NULL：{"type":"NULL"}（无 value 字段）
 * - HIT： {"type":"HIT","value":...}
 *
 * MISS 不落盘：Key 不存在即为 MISS。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CachePayload<T> {

    private String type;

    private T value;

    public static <T> CachePayload<T> nullPayload() {
        return new CachePayload<>("NULL", null);
    }

    public static <T> CachePayload<T> hitPayload(T value) {
        return new CachePayload<>("HIT", value);
    }
}
