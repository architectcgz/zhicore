package com.zhicore.message.infrastructure.config;

import com.zhicore.message.application.port.im.ImConversationQueryGateway;
import com.zhicore.message.application.port.im.ImMessageQueryGateway;
import com.zhicore.message.domain.repository.ConversationRepository;
import com.zhicore.message.domain.repository.MessageRepository;
import com.zhicore.message.infrastructure.integration.localprojection.LocalProjectionImConversationQueryGateway;
import com.zhicore.message.infrastructure.integration.localprojection.LocalProjectionImMessageQueryGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * IM 查询网关装配。
 *
 * 默认使用本地 projection adapter。
 * 当后续接入远端 provider 并注册同名端口实现时，该默认实现会自动让位。
 */
@Configuration
public class ImQueryGatewayConfiguration {

    @Bean
    @ConditionalOnMissingBean(ImConversationQueryGateway.class)
    public ImConversationQueryGateway imConversationQueryGateway(ConversationRepository conversationRepository) {
        return new LocalProjectionImConversationQueryGateway(conversationRepository);
    }

    @Bean
    @ConditionalOnMissingBean(ImMessageQueryGateway.class)
    public ImMessageQueryGateway imMessageQueryGateway(ConversationRepository conversationRepository,
                                                       MessageRepository messageRepository) {
        return new LocalProjectionImMessageQueryGateway(conversationRepository, messageRepository);
    }
}
