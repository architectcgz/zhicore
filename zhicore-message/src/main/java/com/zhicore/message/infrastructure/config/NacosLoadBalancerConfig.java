package com.zhicore.message.infrastructure.config;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.loadbalancer.NacosLoadBalancer;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Nacos LoadBalancer 配置
 * 确保 Feign 客户端使用正确的 Nacos 服务发现 group
 *
 * @author ZhiCore Team
 */
@Configuration
public class NacosLoadBalancerConfig {

    @Bean
    public ReactorLoadBalancer<ServiceInstance> nacosLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory loadBalancerClientFactory,
            NacosDiscoveryProperties nacosDiscoveryProperties) {
        String name = environment.getProperty(LoadBalancerClientFactory.PROPERTY_NAME);
        return new NacosLoadBalancer(
                loadBalancerClientFactory.getLazyProvider(name, ServiceInstanceListSupplier.class),
                name,
                nacosDiscoveryProperties);
    }
}
