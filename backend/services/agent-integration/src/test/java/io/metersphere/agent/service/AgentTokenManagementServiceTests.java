package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentTokenPageRequest;
import io.metersphere.agent.dto.AgentTokenUpdateRequest;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.AgentToken;
import io.metersphere.system.mapper.AgentTokenMapper;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTokenManagementServiceTests {
    @Mock
    private AgentTokenMapper agentTokenMapper;
    @InjectMocks
    private AgentTokenManagementService service;

    @Test
    void personalMutationShouldRejectTokenOwnedByAnotherUser() {
        AgentToken token = new AgentToken();
        token.setId("token-1");
        token.setUserId("user-b");
        when(agentTokenMapper.selectByPrimaryKey("token-1")).thenReturn(token);
        AgentTokenUpdateRequest request = new AgentTokenUpdateRequest();
        request.setId("token-1");
        request.setName("changed");

        try (MockedStatic<SessionUtils> session = Mockito.mockStatic(SessionUtils.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user-a");
            Assertions.assertThrows(MSException.class, () -> service.updatePersonal(request));
            Assertions.assertThrows(MSException.class, () -> service.enablePersonal("token-1"));
            Assertions.assertThrows(MSException.class, () -> service.disablePersonal("token-1"));
            Assertions.assertThrows(MSException.class, () -> service.deletePersonal("token-1"));
        }

        verify(agentTokenMapper, never()).updateByPrimaryKeySelective(Mockito.any());
        verify(agentTokenMapper, never()).deleteByPrimaryKey(Mockito.anyString());
    }

    @Test
    void globalPageShouldPassExpiredStatusAndCurrentTimeToMapper() {
        AgentTokenPageRequest request = new AgentTokenPageRequest();
        request.setKeyword("runner");
        request.setStatus(" expired ");
        when(agentTokenMapper.countPage(Mockito.eq("runner"), Mockito.eq("EXPIRED"), Mockito.anyLong()))
                .thenReturn(0L);
        when(agentTokenMapper.selectPage(Mockito.eq("runner"), Mockito.eq("EXPIRED"), Mockito.anyLong(),
                Mockito.eq(0L), Mockito.eq(10L))).thenReturn(List.of());

        service.page(request);

        verify(agentTokenMapper).countPage(Mockito.eq("runner"), Mockito.eq("EXPIRED"), Mockito.anyLong());
        verify(agentTokenMapper).selectPage(Mockito.eq("runner"), Mockito.eq("EXPIRED"), Mockito.anyLong(),
                Mockito.eq(0L), Mockito.eq(10L));
    }

    @Test
    void globalPageShouldRejectUnknownStatus() {
        AgentTokenPageRequest request = new AgentTokenPageRequest();
        request.setStatus("UNKNOWN");

        Assertions.assertThrows(MSException.class, () -> service.page(request));
        verify(agentTokenMapper, never()).countPage(Mockito.any(), Mockito.any(), Mockito.anyLong());
    }
}
