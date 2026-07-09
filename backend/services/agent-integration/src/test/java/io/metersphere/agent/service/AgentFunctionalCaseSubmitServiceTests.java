package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.sdk.exception.MSException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentFunctionalCaseSubmitServiceTests {

    @InjectMocks
    private AgentFunctionalCaseSubmitService submitService;

    @Mock
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    @Mock
    private AgentCaseSchemaMapper agentCaseSchemaMapper;
    @Mock
    private AgentExecLogService agentExecLogService;
    @Mock
    private AgentAttachmentService agentAttachmentService;
    @Mock
    private FunctionalCaseMapper functionalCaseMapper;
    @Mock
    private SqlSessionFactory sqlSessionFactory;
    @Mock
    private SqlSession sqlSession;

    @Test
    void mismatchedPlanFieldsShouldFailValidation() {
        AgentCaseSubmitRequest request = baseRequest();
        request.setTestPlanId("plan-001");
        request.setTestPlanCaseId(null);

        MSException ex = Assertions.assertThrows(MSException.class, () -> submitService.submit(request));
        Assertions.assertTrue(ex.getMessage().contains("testPlanId"));
    }

    @Test
    void outOfPlanSubmitShouldUpdateCaseAndWriteAuditLog() {
        AgentCaseSubmitRequest request = baseRequest();
        request.setTestPlanId(null);
        request.setTestPlanCaseId(null);

        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId("fc-001");
        when(functionalCaseMapper.selectByPrimaryKey("fc-001")).thenReturn(functionalCase);
        when(agentCaseSchemaMapper.toStepsExecResultJson(any())).thenReturn("[]");
        when(agentExecLogService.log(request, "[]")).thenReturn("log-001");
        when(sqlSessionFactory.openSession(org.apache.ibatis.session.ExecutorType.BATCH)).thenReturn(sqlSession);
        when(sqlSession.getMapper(FunctionalCaseMapper.class)).thenReturn(functionalCaseMapper);
        when(sqlSession.getMapper(FunctionalCaseBlobMapper.class)).thenReturn(org.mockito.Mockito.mock(FunctionalCaseBlobMapper.class));

        submitService.submit(request);

        verify(agentExecLogService).log(request, "[]");
        verify(testPlanFunctionalCaseService, never()).run(any(), any());
    }

    private AgentCaseSubmitRequest baseRequest() {
        AgentCaseSubmitRequest request = new AgentCaseSubmitRequest();
        request.setProjectId("proj-001");
        request.setCaseId("fc-001");
        request.setLastExecResult("SUCCESS");
        request.setExecutedBy("cursor-agent");
        AgentCaseStepDTO step = new AgentCaseStepDTO();
        step.setId("step-1");
        step.setNum(1);
        step.setActualResult("通过");
        step.setExecuteResult("SUCCESS");
        request.setSteps(List.of(step));
        return request;
    }
}
