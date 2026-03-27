package com.zhicore.notification.infrastructure.config;

import org.apache.rocketmq.client.AccessChannel;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQMessageConverter;
import org.apache.rocketmq.spring.support.RocketMQUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * 显式提供 RocketMQ producer/template，避免通知服务首次引入发送能力时因自动装配缺失而无法启动。
 */
@Configuration
@EnableConfigurationProperties(RocketMQProperties.class)
public class RocketMqProducerConfig {

    /**
     * 提供通知服务使用的 MQ producer。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(DefaultMQProducer.class)
    @ConditionalOnProperty(prefix = "rocketmq", name = {"name-server", "producer.group"})
    public DefaultMQProducer defaultMQProducer(RocketMQProperties rocketMQProperties) {
        RocketMQProperties.Producer producerConfig = rocketMQProperties.getProducer();
        String nameServer = rocketMQProperties.getNameServer();
        String groupName = producerConfig.getGroup();

        Assert.hasText(nameServer, "[rocketmq.name-server] must not be null");
        Assert.hasText(groupName, "[rocketmq.producer.group] must not be null");

        DefaultMQProducer producer = RocketMQUtil.createDefaultMQProducer(
                groupName,
                producerConfig.getAccessKey(),
                producerConfig.getSecretKey(),
                producerConfig.isEnableMsgTrace(),
                producerConfig.getCustomizedTraceTopic()
        );
        producer.setNamesrvAddr(nameServer);
        if (StringUtils.hasLength(rocketMQProperties.getAccessChannel())) {
            producer.setAccessChannel(AccessChannel.valueOf(rocketMQProperties.getAccessChannel()));
        }
        producer.setSendMsgTimeout(producerConfig.getSendMessageTimeout());
        producer.setRetryTimesWhenSendFailed(producerConfig.getRetryTimesWhenSendFailed());
        producer.setRetryTimesWhenSendAsyncFailed(producerConfig.getRetryTimesWhenSendAsyncFailed());
        producer.setMaxMessageSize(producerConfig.getMaxMessageSize());
        producer.setCompressMsgBodyOverHowmuch(producerConfig.getCompressMessageBodyThreshold());
        producer.setRetryAnotherBrokerWhenNotStoreOK(producerConfig.isRetryNextServer());
        producer.setUseTLS(producerConfig.isTlsEnable());
        if (StringUtils.hasText(producerConfig.getNamespace())) {
            producer.setNamespace(producerConfig.getNamespace());
        }
        if (StringUtils.hasText(producerConfig.getNamespaceV2())) {
            producer.setNamespaceV2(producerConfig.getNamespaceV2());
        }
        if (StringUtils.hasText(producerConfig.getInstanceName())) {
            producer.setInstanceName(producerConfig.getInstanceName());
        }
        return producer;
    }

    /**
     * starter 未显式提供时，补一个默认消息转换器。
     */
    @Bean
    @ConditionalOnMissingBean(RocketMQMessageConverter.class)
    public RocketMQMessageConverter rocketMQMessageConverter() {
        return new RocketMQMessageConverter();
    }

    /**
     * 为 best-effort fanout 发布补齐 RocketMQTemplate。
     */
    @Bean
    @ConditionalOnMissingBean(RocketMQTemplate.class)
    @ConditionalOnBean({DefaultMQProducer.class, RocketMQMessageConverter.class})
    public RocketMQTemplate rocketMQTemplate(DefaultMQProducer producer,
                                             RocketMQMessageConverter rocketMQMessageConverter) {
        RocketMQTemplate rocketMQTemplate = new RocketMQTemplate();
        rocketMQTemplate.setProducer(producer);
        rocketMQTemplate.setMessageConverter(rocketMQMessageConverter.getMessageConverter());
        return rocketMQTemplate;
    }
}
