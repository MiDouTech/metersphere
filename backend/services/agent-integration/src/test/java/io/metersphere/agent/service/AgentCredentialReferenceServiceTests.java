package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCredentialReferenceDTO;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AgentCredentialReferenceServiceTests {
    @Test void expiredCredentialIsNeverReturnedAsUsable(){
        AgentCredentialReferenceService service=Mockito.spy(new AgentCredentialReferenceService());AgentCredentialReferenceDTO dto=new AgentCredentialReferenceDTO();dto.setId("c1");dto.setProjectId("p1");dto.setEnvironmentId("e1");dto.setBusinessRole("ADMIN");dto.setEnabled(true);dto.setStatus("ACTIVE");dto.setLastVerifyStatus("PASSED");dto.setExpiresAt(System.currentTimeMillis()-1);
        Mockito.doReturn(dto).when(service).getMetadata("c1");
        MSException error=Assertions.assertThrows(MSException.class,()->service.assertUsable("c1","p1","e1","ADMIN"));
        Assertions.assertEquals("CREDENTIAL_REFERENCE_EXPIRED",error.getMessage());
    }
}
