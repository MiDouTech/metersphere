package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentRunnerLeaseDTO;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class AgentTaskClaimServiceTests {
    @AfterEach void clear(){AgentTokenContext.clear();}
    @Test void leaseMutationRequiresSamePersonalAgentToken(){
        AgentTaskClaimService service=new AgentTaskClaimService();AgentExecutionMapper mapper=Mockito.mock(AgentExecutionMapper.class);ReflectionTestUtils.setField(service,"executionMapper",mapper);
        AgentToken token=new AgentToken();token.setId("token-a");AgentTokenContext.set(token);
        AgentRunnerLeaseDTO lease=new AgentRunnerLeaseDTO();lease.setId("l1");lease.setExecutorType("AGENT");lease.setExecutorId("token-b");Mockito.when(mapper.selectLeaseById("l1")).thenReturn(lease);
        MSException error=Assertions.assertThrows(MSException.class,()->service.assertLeaseOwner("l1"));
        Assertions.assertEquals("AGENT_TASK_LEASE_FORBIDDEN",error.getMessage());
    }
}
