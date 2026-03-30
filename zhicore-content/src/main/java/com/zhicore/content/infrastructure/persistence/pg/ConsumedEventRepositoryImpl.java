package com.zhicore.content.infrastructure.persistence.pg;

import com.zhicore.content.application.port.repo.ConsumedEventRepository;
import com.zhicore.content.infrastructure.persistence.pg.entity.ConsumedEventEntity;
import com.zhicore.content.infrastructure.persistence.pg.mapper.ConsumedEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

/**
 * 已消费事件仓储实现
 * 
 * 使用数据库主键约束实现幂等性检查
 * 避免重启后丢失去重信息（相比内存 Set 的优势）
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ConsumedEventRepositoryImpl implements ConsumedEventRepository {
    private final ConsumedEventMapper mapper;
    
    /**
     * 尝试插入消费记录
     * 
     * 利用数据库主键约束实现幂等性：
     * - 如果 eventId 不存在，插入成功，返回 true
     * - 如果 eventId 已存在，抛出 DuplicateKeyException，返回 false
     * 
     * @param eventId 事件 ID
     * @param eventType 事件类型
     * @param consumerName 消费者名称
     * @return 是否是新事件（true=首次消费，false=重复消费）
     */
    @Override
    public boolean tryInsert(String eventId, String eventType, String consumerName) {
        try {
            ConsumedEventEntity entity = new ConsumedEventEntity();
            entity.setEventId(eventId);
            entity.setEventType(eventType);
            entity.setConsumerName(consumerName);
            entity.setConsumedAt(OffsetDateTime.now());
            
            int inserted = mapper.insert(entity);
            
            if (inserted > 0) {
                log.debug("Event consumed for the first time: eventId={}, type={}, consumer={}", 
                    eventId, eventType, consumerName);
                return true;  // 插入成功，是新事件
            } else {
                log.warn("Failed to insert consumed event: eventId={}, type={}, consumer={}", 
                    eventId, eventType, consumerName);
                return false;
            }
            
        } catch (DuplicateKeyException e) {
            // 主键冲突，说明已经消费过
            log.info("Event already consumed (duplicate): eventId={}, type={}, consumer={}", 
                eventId, eventType, consumerName);
            return false;
        } catch (Exception e) {
            // 其他异常，记录日志并返回 false（保守处理，避免重复消费）
            log.error("Error inserting consumed event: eventId={}, type={}, consumer={}", 
                eventId, eventType, consumerName, e);
            return false;
        }
    }
    
    /**
     * 清理过期的消费记录
     * 
     * 定期清理旧记录，避免 consumed_events 表无限增长
     * 建议保留 30 天的记录
     * 
     * @param before 清理此时间之前的记录
     * @return 清理的记录数量
     */
    @Override
    public int cleanupBefore(OffsetDateTime before) {
        try {
            int deleted = mapper.deleteBefore(before);
            if (deleted > 0) {
                log.info("Cleaned up {} consumed event records before {}", deleted, before);
            }
            return deleted;
        } catch (Exception e) {
            log.error("Error cleaning up consumed events before {}", before, e);
            return 0;
        }
    }
}
