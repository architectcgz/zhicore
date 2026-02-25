package com.zhicore.migration.infrastructure.cdc;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * CDC 事件
 */
@Data
@Builder
public class CdcEvent {

    /**
     * 操作类型
     */
    private Operation operation;

    /**
     * 表名
     */
    private String table;

    /**
     * 变更前数据
     */
    private Map<String, Object> before;

    /**
     * 变更后数据
     */
    private Map<String, Object> after;

    /**
     * 事务 ID
     */
    private Long transactionId;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 操作类型枚举
     */
    public enum Operation {
        /**
         * 插入
         */
        CREATE,
        /**
         * 更新
         */
        UPDATE,
        /**
         * 删除
         */
        DELETE,
        /**
         * 读取（快照）
         */
        READ
    }
}
