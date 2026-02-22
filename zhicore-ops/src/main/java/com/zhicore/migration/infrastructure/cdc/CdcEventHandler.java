package com.zhicore.migration.infrastructure.cdc;

import io.debezium.engine.RecordChangeEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.connect.data.Field;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * CDC 事件处理器
 * 解析 Debezium 事件并分发到对应的消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CdcEventHandler {

    private final PostStatsCdcConsumer postStatsCdcConsumer;
    private final PostLikesCdcConsumer postLikesCdcConsumer;
    private final CommentStatsCdcConsumer commentStatsCdcConsumer;
    private final CommentLikesCdcConsumer commentLikesCdcConsumer;
    private final UserFollowStatsCdcConsumer userFollowStatsCdcConsumer;

    /**
     * 处理变更事件
     */
    public void handleChangeEvent(RecordChangeEvent<SourceRecord> recordChangeEvent) {
        SourceRecord sourceRecord = recordChangeEvent.record();
        
        if (sourceRecord.value() == null) {
            return;
        }

        Struct sourceRecordValue = (Struct) sourceRecord.value();
        
        // 获取操作类型
        String operationStr = sourceRecordValue.getString("op");
        CdcEvent.Operation operation = parseOperation(operationStr);
        
        if (operation == null) {
            return;
        }

        // 获取表名
        Struct source = sourceRecordValue.getStruct("source");
        String table = source.getString("table");
        
        // 获取变更前后数据
        Map<String, Object> before = extractData(sourceRecordValue.getStruct("before"));
        Map<String, Object> after = extractData(sourceRecordValue.getStruct("after"));
        
        // 构建 CDC 事件
        CdcEvent cdcEvent = CdcEvent.builder()
                .operation(operation)
                .table(table)
                .before(before)
                .after(after)
                .transactionId(source.getInt64("txId"))
                .timestamp(source.getInt64("ts_ms"))
                .build();

        log.debug("收到 CDC 事件: table={}, operation={}", table, operation);

        // 分发到对应的消费者
        dispatchEvent(cdcEvent);
    }

    /**
     * 分发事件到对应的消费者
     */
    private void dispatchEvent(CdcEvent event) {
        switch (event.getTable()) {
            case "post_stats" -> postStatsCdcConsumer.consume(event);
            case "post_likes" -> postLikesCdcConsumer.consume(event);
            case "comment_stats" -> commentStatsCdcConsumer.consume(event);
            case "comment_likes" -> commentLikesCdcConsumer.consume(event);
            case "user_follow_stats" -> userFollowStatsCdcConsumer.consume(event);
            default -> log.warn("未知的表: {}", event.getTable());
        }
    }

    /**
     * 解析操作类型
     */
    private CdcEvent.Operation parseOperation(String op) {
        return switch (op) {
            case "c" -> CdcEvent.Operation.CREATE;
            case "u" -> CdcEvent.Operation.UPDATE;
            case "d" -> CdcEvent.Operation.DELETE;
            case "r" -> CdcEvent.Operation.READ;
            default -> null;
        };
    }

    /**
     * 从 Struct 中提取数据
     */
    private Map<String, Object> extractData(Struct struct) {
        if (struct == null) {
            return null;
        }
        
        Map<String, Object> data = new HashMap<>();
        for (Field field : struct.schema().fields()) {
            data.put(field.name(), struct.get(field));
        }
        return data;
    }
}
