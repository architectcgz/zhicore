package com.zhicore.ranking;

import com.zhicore.ranking.infrastructure.feign.PostServiceClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 排行榜服务启动类
 *
 * @author ZhiCore Team
 */
@SpringBootApplication(scanBasePackages = {"com.zhicore.ranking", "com.zhicore.common"})
@EnableDiscoveryClient
@EnableFeignClients(clients = {
        PostServiceClient.class
})
@EnableScheduling
public class RankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(RankingApplication.class, args);
    }
}
