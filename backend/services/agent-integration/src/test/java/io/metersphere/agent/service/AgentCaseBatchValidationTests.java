package io.metersphere.agent.service;

import io.metersphere.agent.dto.*;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.system.domain.CustomFieldOption;
import io.metersphere.system.dto.sdk.*;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentCaseBatchValidationTests {
    @Test void mixedBatchRejectsBadRowsAndPersistsOnlyValidCase(){
        var service=new AgentCaseWriteService();var definitions=mock(FunctionalCaseService.class);
        var projects=mock(AgentProjectService.class);when(projects.resolveProjectId("p")).thenReturn("p");
        var mapper=mock(ProjectMapper.class);var project=new Project();project.setId("p");project.setOrganizationId("o");when(mapper.selectByPrimaryKey("p")).thenReturn(project);
        var templates=mock(ProjectTemplateService.class);var template=new TemplateDTO();template.setId("t");
        var field=new TemplateCustomFieldDTO();field.setFieldId("priority");field.setInternalFieldKey("functional_priority");var option=new CustomFieldOption();option.setValue("P0");field.setOptions(List.of(option));template.setCustomFields(List.of(field));
        when(templates.getDefaultTemplateDTO("p","FUNCTIONAL")).thenReturn(template);
        var created=new FunctionalCase();created.setId("c");created.setNum(1L);created.setName("valid");when(definitions.addFunctionalCase(any(),anyList(),any(),eq("o"))).thenReturn(created);
        ReflectionTestUtils.setField(service,"functionalCaseService",definitions);ReflectionTestUtils.setField(service,"projectMapper",mapper);
        ReflectionTestUtils.setField(service,"agentProjectService",projects);ReflectionTestUtils.setField(service,"projectTemplateService",templates);
        ReflectionTestUtils.setField(service,"access",mock(AgentBusinessAccess.class));ReflectionTestUtils.setField(service,"safeErrors",new AgentSafeErrorMapper());
        ReflectionTestUtils.setField(service,"agentCaseSchemaMapper",new io.metersphere.agent.mapper.AgentCaseSchemaMapper());ReflectionTestUtils.setField(service,"agentExecLogService",mock(AgentExecLogService.class));
        var blank=new AgentCaseCreateItem();blank.setName(" ");var badPriority=new AgentCaseCreateItem();badPriority.setName("bad");badPriority.setPriority("P9");var valid=new AgentCaseCreateItem();valid.setName("valid");
        var request=new AgentCaseBatchCreateRequest();request.setProjectId("p");request.setModuleId("root");request.setCases(List.of(blank,badPriority,valid));
        try(var session=mockStatic(SessionUtils.class)){
            session.when(SessionUtils::getUserId).thenReturn("u");var result=service.batchCreate(request);
            assertEquals(1,result.getCreated().size());assertEquals(2,result.getErrors().size());
            assertEquals("CASE_NAME_INVALID",result.getErrors().get(0).getCode());assertEquals(1,result.getErrors().get(0).getRow());
            assertEquals("CASE_PRIORITY_INVALID",result.getErrors().get(1).getCode());
            verify(definitions,times(1)).addFunctionalCase(any(),anyList(),any(),eq("o"));
        }
    }
}
