package io.metersphere.agent.service;

import io.metersphere.agent.tool.AgentMcpToolHandler;
import io.metersphere.agent.tool.AgentMcpToolExposure;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Every discoverable business writer must declare an atomic or resource-owned replay protocol. */
class AgentMcpWriteRoutingTests {
    @Test void allBusinessWritersHaveExplicitReplayRouting() throws Exception {
        var scanner=new ClassPathScanningCandidateComponentProvider(true);
        var writers=new TreeSet<String>();
        for(var bean:scanner.findCandidateComponents("io.metersphere.agent.tool")) {
            Class<?> type=Class.forName(bean.getBeanClassName());
            if(AgentMcpToolHandler.class.isAssignableFrom(type)) {
                inspect((AgentMcpToolHandler)type.getDeclaredConstructor().newInstance(),writers);
            } else if(type.isAnnotationPresent(org.springframework.context.annotation.Configuration.class)) {
                Object config=type.getDeclaredConstructor().newInstance();
                for(var method:type.getDeclaredMethods())if(method.isAnnotationPresent(org.springframework.context.annotation.Bean.class)&&AgentMcpToolHandler.class.isAssignableFrom(method.getReturnType())) {
                    method.setAccessible(true);
                    Object[] args=Arrays.stream(method.getParameterTypes()).map(org.mockito.Mockito::mock).toArray();
                    inspect((AgentMcpToolHandler)method.invoke(config,args),writers);
                }
            }
        }
        assertTrue(writers.size()>20,"Tool inventory must not silently pass with an empty classpath");
    }
    private static void inspect(AgentMcpToolHandler handler,Set<String> writers){
        String name=handler.name();
        if(handler.readOnlyHint()||AgentMcpToolExposure.legacyResultWrite(name))return;
        if(name.startsWith("metersphere.execution.")||name.startsWith("metersphere.task.")||name.startsWith("metersphere.human_request.")||name.startsWith("metersphere.artifact."))return;
        writers.add(name);
        boolean resourceOwned=Boolean.TRUE.equals(org.springframework.test.util.ReflectionTestUtils.invokeMethod(AgentMcpStreamableService.class,"bypassesGenericIdempotency",name));
        assertTrue(resourceOwned||AgentGenericMcpWriteService.handles(name),"Missing current-resource replay authorization/atomic protocol: "+name);
    }
}
