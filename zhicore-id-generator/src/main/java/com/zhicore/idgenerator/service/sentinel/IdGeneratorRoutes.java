package com.zhicore.idgenerator.service.sentinel;

/**
 * ID 生成服务代表性 URL 路由常量。
 */
public final class IdGeneratorRoutes {

    private IdGeneratorRoutes() {
    }

    public static final String PREFIX = "/api/v1/id";
    public static final String SNOWFLAKE = PREFIX + "/snowflake";
}
