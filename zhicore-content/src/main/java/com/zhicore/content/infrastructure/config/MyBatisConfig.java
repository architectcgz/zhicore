package com.zhicore.content.infrastructure.config;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.zhicore.content.domain.model.PostId;
import com.zhicore.content.domain.model.TagId;
import com.zhicore.content.domain.model.TopicId;
import com.zhicore.content.domain.model.UserId;
import com.zhicore.content.infrastructure.persistence.pg.typehandler.PostIdTypeHandler;
import com.zhicore.content.infrastructure.persistence.pg.typehandler.TagIdTypeHandler;
import com.zhicore.content.infrastructure.persistence.pg.typehandler.TopicIdTypeHandler;
import com.zhicore.content.infrastructure.persistence.pg.typehandler.UserIdTypeHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis 配置
 * 
 * 配置 MyBatis 相关的拦截器、插件和类型处理器
 *
 * @author ZhiCore Team
 */
@Configuration
public class MyBatisConfig {

    /**
     * 注册慢查询拦截器
     */
    @Bean
    public SlowQueryInterceptor slowQueryInterceptor(PerformanceProperties performanceProperties) {
        return new SlowQueryInterceptor(performanceProperties);
    }
    
    /**
     * 注册自定义类型处理器
     * 
     * 用于自动转换值对象 ID 和数据库 BIGINT 类型
     */
    @Bean
    public ConfigurationCustomizer mybatisConfigurationCustomizer() {
        return configuration -> {
            // 注册值对象 ID 类型处理器
            configuration.getTypeHandlerRegistry().register(PostId.class, PostIdTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(UserId.class, UserIdTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(TagId.class, TagIdTypeHandler.class);
            configuration.getTypeHandlerRegistry().register(TopicId.class, TopicIdTypeHandler.class);
        };
    }
}
