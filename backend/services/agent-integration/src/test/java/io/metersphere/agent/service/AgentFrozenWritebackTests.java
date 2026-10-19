package io.metersphere.agent.service;
import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.*;
import io.metersphere.agent.quality.GlobalQualityGate;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.plan.dto.request.TestPlanCaseRunRequest;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.mockito.Mockito.*;
class AgentFrozenWritebackTests {
    @Test void planWritebackUsesFrozenHistoryEntryPointAndTransactionalSyncMarker(){
        var service=new AgentExecutionCaseWritebackService();var mapper=mock(AgentExecutionMapper.class);var cases=mock(FunctionalCaseMapper.class);var plans=mock(TestPlanFunctionalCaseService.class);var finalization=mock(AgentExecutionFinalizationService.class);
        ReflectionTestUtils.setField(service,"executionMapper",mapper);ReflectionTestUtils.setField(service,"functionalCaseMapper",cases);ReflectionTestUtils.setField(service,"testPlanFunctionalCaseService",plans);ReflectionTestUtils.setField(service,"finalization",finalization);ReflectionTestUtils.setField(service,"qualityGate",mock(GlobalQualityGate.class));ReflectionTestUtils.setField(service,"caseSchemaMapper",new AgentCaseSchemaMapper());ReflectionTestUtils.setField(service,"functionalCaseService",mock(FunctionalCaseService.class));
        var task=new AgentExecutionTaskDTO();task.setId("t1");task.setProjectId("p1");task.setCurrentExecutionId("e1");
        var c=new AgentExecutionCaseDTO();c.setId("ec1");c.setCaseId("c1");c.setStatus("SUCCESS");c.setTestPlanCaseId("pc1");c.setTestPlanId("plan1");
        var source=new FunctionalCase();source.setId("c1");source.setProjectId("p1");when(cases.selectByPrimaryKey("c1")).thenReturn(source);
        try(var ids=mockStatic(IDGenerator.class)){ids.when(IDGenerator::nextStr).thenReturn("idem1");service.writeback(task,c,List.of());}
        verify(plans).runFrozenResult(any(TestPlanCaseRunRequest.class),any());verify(plans,never()).run(any(),any());
        verify(finalization).beginSync(task,c);verify(finalization).synced(task,c);
        var gate=(GlobalQualityGate)ReflectionTestUtils.getField(service,"qualityGate");
        clearInvocations(gate,plans);
        when(mapper.countWritebackIdempotency(eq("t1"),eq("c1"),anyString())).thenReturn(1);
        service.writeback(task,c,List.of());
        verifyNoInteractions(gate,plans);
        verify(finalization,times(2)).synced(task,c);

    }
}
