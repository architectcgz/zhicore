package com.zhicore.upload.infrastructure.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.zhicore.common.sentinel.FlowRuleSupport;
import com.zhicore.upload.infrastructure.sentinel.UploadRoutes;
import com.zhicore.upload.infrastructure.sentinel.UploadSentinelResources;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件上传服务 Sentinel 规则。
 */
@Slf4j
@Configuration
public class UploadSentinelConfig {

    private final UploadSentinelProperties properties;

    public UploadSentinelConfig(UploadSentinelProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void initFlowRules() {
        if (!properties.isEnabled()) {
            log.info("文件上传服务 Sentinel 规则已禁用");
            return;
        }

        List<FlowRule> rules = new ArrayList<>();
        rules.add(buildRule(UploadRoutes.IMAGE, properties.getUploadImageQps()));
        rules.add(buildRule(UploadSentinelResources.UPLOAD_IMAGE, properties.getUploadImageQps()));
        rules.add(buildRule(UploadSentinelResources.UPLOAD_AUDIO, properties.getUploadAudioQps()));
        rules.add(buildRule(UploadSentinelResources.UPLOAD_IMAGES_BATCH, properties.getUploadImagesBatchQps()));
        rules.add(buildRule(UploadSentinelResources.GET_FILE_URL, properties.getGetFileUrlQps()));
        rules.add(buildRule(UploadSentinelResources.DELETE_FILE, properties.getDeleteFileQps()));

        FlowRuleSupport.loadMergedRules(rules);
        log.info("文件上传服务 Sentinel 规则已加载: uploadImage={}qps, uploadAudio={}qps, batch={}qps, " +
                        "getFileUrl={}qps, deleteFile={}qps",
                properties.getUploadImageQps(), properties.getUploadAudioQps(),
                properties.getUploadImagesBatchQps(), properties.getGetFileUrlQps(),
                properties.getDeleteFileQps());
    }

    private FlowRule buildRule(String resource, int qps) {
        return new FlowRule(resource)
                .setGrade(RuleConstant.FLOW_GRADE_QPS)
                .setCount(qps)
                .setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP)
                .setWarmUpPeriodSec(properties.getWarmUpPeriodSec());
    }
}
