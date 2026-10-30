package io.metersphere.agent.tool;

import io.metersphere.agent.service.*;
import io.metersphere.agent.tool.functional.*;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.system.domain.AgentToken;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersonalExecutionProjectReferenceTests {
    @Test void operationLookupResolvesNameBeforePermissionAndResourceLookup(){
        var tool=new PersonalOperationGetTool();var projects=mock(AgentProjectService.class);var access=mock(AgentBusinessAccess.class);var jdbc=mock(JdbcTemplate.class);
        when(projects.resolveProjectId("Project Name")).thenReturn("p");
        ReflectionTestUtils.setField(tool,"projects",projects);ReflectionTestUtils.setField(tool,"access",access);ReflectionTestUtils.setField(tool,"jdbc",jdbc);
        var token=new AgentToken();token.setId("t");AgentTokenContext.set(token);
        try {
            var result=(Map<?,?>)tool.execute(Map.of("projectId","Project Name","caseId","c","requestId","r","toolName","metersphere.functional.execution.submit"));
            assertEquals("NOT_FOUND",result.get("state"));verify(access).caseProject("c","p","FUNCTIONAL_CASE:READ",false,"FUNCTIONAL_READ");
        } finally {AgentTokenContext.clear();}
    }
    @Test void publishedImageReadUsesCanonicalProjectId()throws Exception {
        var tool=new FunctionalAttachmentReadTool();var projects=mock(AgentProjectService.class);var access=mock(AgentBusinessAccess.class);var jdbc=mock(JdbcTemplate.class);
        var files=mock(io.metersphere.functional.service.FunctionalCaseAttachmentService.class);
        when(projects.resolveProjectId("100001")).thenReturn("p");
        ReflectionTestUtils.setField(tool,"projects",projects);ReflectionTestUtils.setField(tool,"access",access);ReflectionTestUtils.setField(tool,"jdbc",jdbc);ReflectionTestUtils.setField(tool,"attachments",files);
        var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_RGB);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        when(jdbc.queryForList(anyString(),eq(Long.class),eq("c"),eq("f"))).thenReturn(List.of((long)bytes.size()));
        when(files.getFileByte(any())).thenReturn(bytes.toByteArray());
        assertInstanceOf(AgentMcpImageResult.class,tool.execute(Map.of("projectId","100001","caseId","c","fileId","f")));
        verify(files).getFileByte(argThat(r->"p".equals(r.getProjectId())));
    }
}
