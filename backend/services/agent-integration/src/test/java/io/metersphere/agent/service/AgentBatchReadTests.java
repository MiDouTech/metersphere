package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.functional.domain.*;
import io.metersphere.functional.mapper.*;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.system.dto.sdk.TemplateDTO;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import io.metersphere.system.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentBatchReadTests {
    private Object previousMessages;
    @org.junit.jupiter.api.BeforeEach void messages() {
        previousMessages = ReflectionTestUtils.getField(io.metersphere.sdk.util.Translator.class, "messageSource");
        ReflectionTestUtils.setField(io.metersphere.sdk.util.Translator.class, "messageSource", new org.springframework.context.support.StaticMessageSource());
    }
    @org.junit.jupiter.api.AfterEach void restoreMessages() {
        ReflectionTestUtils.setField(io.metersphere.sdk.util.Translator.class, "messageSource", previousMessages);
    }
    @Test void hundredCasesShareTemplateAndBulkQueriesWithoutLeakingFieldValues() {
        var service=new FunctionalCaseService();
        var base=mock(FunctionalCaseMapper.class);var blob=mock(ExtFunctionalCaseMapper.class);
        var fields=mock(FunctionalCaseCustomFieldMapper.class);var templates=mock(ProjectTemplateService.class);
        var users=mock(UserMapper.class);
        ReflectionTestUtils.setField(service,"functionalCaseMapper",base);
        ReflectionTestUtils.setField(service,"extFunctionalCaseMapper",blob);
        ReflectionTestUtils.setField(service,"functionalCaseCustomFieldMapper",fields);
        ReflectionTestUtils.setField(service,"projectTemplateService",templates);
        ReflectionTestUtils.setField(service,"userMapper",users);
        List<FunctionalCase> cases=new ArrayList<>();List<FunctionalCaseBlob> bodies=new ArrayList<>();
        List<FunctionalCaseCustomField> values=new ArrayList<>();List<String> ids=new ArrayList<>();
        for(int i=0;i<100;i++) {
            String id="c"+i;ids.add(id);
            var item=new FunctionalCase();item.setId(id);item.setProjectId("p");item.setTemplateId("t");cases.add(item);
            var body=new FunctionalCaseBlob();body.setId(id);body.setSteps("[]".getBytes(java.nio.charset.StandardCharsets.UTF_8));bodies.add(body);
            var value=new FunctionalCaseCustomField();value.setCaseId(id);value.setFieldId("f");value.setValue("value-"+i);values.add(value);
        }
        var template=new TemplateDTO();var field=new TemplateCustomFieldDTO();
        field.setFieldId("f");field.setFieldName("custom");field.setType("INPUT");field.setDefaultValue("default");
        template.setCustomFields(List.of(field));
        when(base.selectByExample(any())).thenReturn(cases);
        when(blob.getAgentContent(ids,true)).thenReturn(bodies);
        when(fields.selectByExample(any())).thenReturn(values);
        when(templates.getTemplateDTOById(eq("t"),eq("p"),any())).thenReturn(template);
        var result=service.getAgentCaseDetails(ids,"p",true);
        assertEquals(100,result.size());
        for(int i=0;i<100;i++) assertEquals("value-"+i,result.get("c"+i).getCustomFields().getFirst().getDefaultValue());
        assertEquals("default",field.getDefaultValue());
        verify(base,times(1)).selectByExample(any());verify(blob,times(1)).getAgentContent(ids,true);
        verify(fields,times(1)).selectByExample(any());verify(templates,times(1)).getTemplateDTOById(eq("t"),eq("p"),any());
        verifyNoInteractions(users);
    }

    @Test void missingOrForeignTargetsFailBeforeReadingTheirBodies() {
        var service=new FunctionalCaseService();var base=mock(FunctionalCaseMapper.class);var blob=mock(ExtFunctionalCaseMapper.class);
        ReflectionTestUtils.setField(service,"functionalCaseMapper",base);ReflectionTestUtils.setField(service,"extFunctionalCaseMapper",blob);
        when(base.selectByExample(any())).thenReturn(List.of());
        assertThrows(RuntimeException.class,()->service.getAgentCaseDetails(List.of("foreign"),"authorized",false));
        var example=org.mockito.ArgumentCaptor.forClass(FunctionalCaseExample.class);
        verify(base).selectByExample(example.capture());
        assertTrue(example.getValue().getOredCriteria().getFirst().getAllCriteria().stream()
                .anyMatch(c->c.getCondition().equals("project_id =") && c.getValue().equals("authorized")));
        verifyNoInteractions(blob);
    }

    @Test void fiveHundredDetailsUseThreeBoundedBatchesAndPreservePlanExecutionMetadata() {
        var cases=mock(FunctionalCaseService.class);var service=new AgentFunctionalCaseExportService();
        ReflectionTestUtils.setField(service,"functionalCaseService",cases);
        ReflectionTestUtils.setField(service,"agentCaseSchemaMapper",new AgentCaseSchemaMapper());
        when(cases.getAgentCaseDetails(anyList(),eq("p"),eq(true))).thenAnswer(call->{
            Map<String,io.metersphere.functional.dto.FunctionalCaseDetailDTO> result=new HashMap<>();
            for(String id:call.<List<String>>getArgument(0)) {
                var d=new io.metersphere.functional.dto.FunctionalCaseDetailDTO();d.setId(id);d.setProjectId("p");d.setSteps("[]");d.setLastExecuteUser("repository");result.put(id,d);
            }
            return result;
        });
        List<AgentCaseDTO> rows=new ArrayList<>();
        for(int i=0;i<500;i++){var row=new AgentCaseDTO();row.setCaseId("c"+i);row.setProjectId("p");row.setTestPlanId("plan");row.setLastExecuteUser("plan-user");rows.add(row);}
        service.enrichWithSteps(rows,new ArrayList<>());
        verify(cases,times(3)).getAgentCaseDetails(argThat(ids->ids.size()<=200),eq("p"),eq(true));
        assertTrue(rows.stream().allMatch(row->"plan-user".equals(row.getLastExecuteUser())));
        verify(cases,never()).getFunctionalCaseDetail(anyString(),any(),anyBoolean());
    }
}
