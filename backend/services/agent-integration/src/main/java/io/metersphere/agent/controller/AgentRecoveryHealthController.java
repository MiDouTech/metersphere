package io.metersphere.agent.controller;

import io.metersphere.agent.service.AgentRecoveryScanHealth;
import io.metersphere.sdk.constants.PermissionConstants;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping({"/ai/recovery/status","/api/ai/recovery/status"})
public class AgentRecoveryHealthController {
    private final AgentRecoveryScanHealth health;
    private final org.springframework.context.ApplicationContext context;
    public AgentRecoveryHealthController(AgentRecoveryScanHealth health, org.springframework.context.ApplicationContext context){this.health=health;this.context=context;}
    @GetMapping
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public List<Map<String,Object>> status(){return health.status();}

    @GetMapping("/facts-readiness")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public Map<String,Object> factsReadiness() {
        boolean ready=context.getBean(io.metersphere.agent.security.AgentFactCipher.class).configured();
        return Map.of("ready",ready,"code",ready?"READY":"QUALITY_FACTS_KEY_NOT_CONFIGURED");
    }

    @GetMapping("/runtime")
    @RequiresPermissions(PermissionConstants.AI_EXECUTION_READ)
    public Map<String,Object> runtime() {
        var runtime=Runtime.getRuntime();
        var pools=new ArrayList<Map<String,Object>>();
        context.getBeansOfType(javax.sql.DataSource.class).forEach((name,source)->{
            if(source instanceof com.zaxxer.hikari.HikariDataSource hikari && hikari.getHikariPoolMXBean()!=null) {
                var pool=hikari.getHikariPoolMXBean();
                pools.add(Map.of("name",name,"active",pool.getActiveConnections(),"idle",pool.getIdleConnections(),"pending",pool.getThreadsAwaitingConnection(),"total",pool.getTotalConnections()));
            }
        });
        var gc=java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(bean->Map.of("name",bean.getName(),"count",bean.getCollectionCount(),"timeMs",bean.getCollectionTime())).toList();
        return Map.of("capturedAt",System.currentTimeMillis(),"heapUsedBytes",runtime.totalMemory()-runtime.freeMemory(),
                "heapMaxBytes",runtime.maxMemory(),"threads",java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount(),
                "systemLoad",java.lang.management.ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage(),"pools",pools,"gc",gc);
    }
}
