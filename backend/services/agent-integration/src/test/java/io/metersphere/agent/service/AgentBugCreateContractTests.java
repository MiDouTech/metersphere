package io.metersphere.agent.service;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.bug.domain.Bug;
import io.metersphere.bug.dto.request.BugEditRequest;
import io.metersphere.bug.service.BugService;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.system.dto.sdk.TemplateDTO;
import io.metersphere.system.dto.sdk.TemplateCustomFieldDTO;
import io.metersphere.system.domain.CustomFieldOption;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class AgentBugCreateContractTests {
    private TemplateDTO template(){
        var t=new TemplateDTO();t.setId("template");var fields=new ArrayList<TemplateCustomFieldDTO>();
        for(String id:List.of("status","handleUser","degree")){var f=new TemplateCustomFieldDTO();f.setFieldId(id);f.setRequired(true);f.setType("handleUser".equals(id)?"MULTIPLE_MEMBER":"SELECT");
            var o=new CustomFieldOption();o.setValue("handleUser".equals(id)?"member":"general");f.setOptions(List.of(o));fields.add(f);}
        t.setCustomFields(fields);return t;
    }
    @Test void missingHandlerIsActionable(){var s=new AgentBugWriteService();assertEquals("BUG_REQUIRED_FIELD_MISSING",assertThrows(MSException.class,()->ReflectionTestUtils.invokeMethod(s,"buildCreateFields",template(),Map.of("degree","general"),"user")).getMessage());}
    @Test void rejectsForeignHandler(){var s=new AgentBugWriteService();assertEquals("BUG_FIELD_VALUE_INVALID",assertThrows(MSException.class,()->ReflectionTestUtils.invokeMethod(s,"buildCreateFields",template(),Map.of("degree","general","handleUser","foreign"),"user")).getMessage());}
    @Test void cannotChooseArbitraryInitialState(){var s=new AgentBugWriteService();assertEquals("BUG_INITIAL_STATUS_SERVER_OWNED",assertThrows(MSException.class,()->ReflectionTestUtils.invokeMethod(s,"buildCreateFields",template(),Map.of("status","CLOSED"),"user")).getMessage());}
    @Test void createLeavesIdUnsetAndSuppliesServerOwnedStatus(){
        var s=new AgentBugWriteService();var projects=mock(AgentProjectService.class);var mapper=mock(ProjectMapper.class);var templates=mock(ProjectTemplateService.class);var backend=mock(BugService.class);var versions=mock(TestAssetVersionService.class);
        ReflectionTestUtils.setField(s,"agentProjectService",projects);ReflectionTestUtils.setField(s,"projectMapper",mapper);ReflectionTestUtils.setField(s,"projectTemplateService",templates);ReflectionTestUtils.setField(s,"bugService",backend);ReflectionTestUtils.setField(s,"testAssetVersionService",versions);ReflectionTestUtils.setField(s,"agentExecLogService",mock(AgentExecLogService.class));
        var applications=mock(io.metersphere.project.service.ProjectApplicationService.class);ReflectionTestUtils.setField(s,"applications",applications);when(applications.getPlatformName("p")).thenReturn("Local");
        when(projects.resolveProjectId("p")).thenReturn("p");var p=new Project();p.setId("p");p.setOrganizationId("org");when(mapper.selectByPrimaryKey("p")).thenReturn(p);
        when(templates.getDefaultTemplateDTO("p","BUG")).thenReturn(template());when(backend.getTemplate("template","p",null,null,false,"zh_CN")).thenReturn(template());
        var bug=new Bug();bug.setId("created");bug.setProjectId("p");bug.setNum(1);bug.setTitle("title");bug.setStatus("initial");when(backend.addOrUpdateFromTrustedSource(any(),any(),eq("user"),eq("org"),eq("AI"),any())).thenReturn(bug);when(versions.publish(any(),any(),any(),any(),any(),any())).thenReturn(new TestAssetVersionDTO());
        var req=new AgentBugCreateRequest();req.setProjectId("p");req.setTitle("title");req.setCustomFields(Map.of("handleUser","member","degree","general"));
        ReflectionTestUtils.invokeMethod(s,"createInternal",req,"user","AI");
        var captured=org.mockito.ArgumentCaptor.forClass(BugEditRequest.class);verify(backend).addOrUpdateFromTrustedSource(captured.capture(),any(),eq("user"),eq("org"),eq("AI"),any());assertNull(captured.getValue().getId());assertEquals("",captured.getValue().getCustomFields().stream().filter(f->"status".equals(f.getId())).findFirst().orElseThrow().getValue());
    }
}
