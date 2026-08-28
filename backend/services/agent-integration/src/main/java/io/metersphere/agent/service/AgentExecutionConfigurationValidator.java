package io.metersphere.agent.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AgentExecutionConfigurationValidator {
    @Resource private Environment environment;

    @PostConstruct
    public void validate() {
        Map<String,long[]> ranges=new LinkedHashMap<>();
        ranges.put("agent.execution.lease-ttl-ms",new long[]{10_000,3_600_000});
        ranges.put("agent.execution.runner-heartbeat-ms",new long[]{1_000,300_000});
        ranges.put("agent.execution.trigger-scan-ms",new long[]{1_000,3_600_000});
        ranges.put("agent.execution.reconcile-scan-ms",new long[]{1_000,3_600_000});
        ranges.put("metersphere.ai.execution.preflight-ttl-ms",new long[]{10_000,3_600_000});
        ranges.put("metersphere.ai.map-gateway.connect-timeout-ms",new long[]{100,60_000});
        ranges.put("metersphere.ai.map-gateway.request-timeout-ms",new long[]{1_000,600_000});
        ranges.put("agent.execution.artifact-max-bytes",new long[]{1_024,104_857_600});
        ranges.put("agent.execution.artifact-retention-ms",new long[]{60_000,31_536_000_000L});
        ranges.put("agent.execution.data-cleanup-max-attempts",new long[]{1,100});
        ranges.forEach((key,range)->{
            Long value=environment.getProperty(key,Long.class);
            if(value!=null&&(value<range[0]||value>range[1]))throw new IllegalStateException("Invalid AI execution configuration: "+key);
        });
        String gateway=environment.getProperty("metersphere.ai.map-gateway.base-url");
        if(StringUtils.isNotBlank(gateway)){
            URI uri=URI.create(gateway);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null)throw new IllegalStateException("Invalid AI execution configuration: metersphere.ai.map-gateway.base-url");
        }
    }
}
