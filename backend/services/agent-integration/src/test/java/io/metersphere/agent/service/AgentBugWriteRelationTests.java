package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.TestAssetVersionDTO;
import io.metersphere.bug.domain.Bug;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentBugWriteRelationTests {

    @Test
    void createdBugShouldPublishCaseReportsBugRelation() {
        TestAssetVersionService versionService = mock(TestAssetVersionService.class);
        FunctionalCaseMapper functionalCaseMapper = mock(FunctionalCaseMapper.class);
        AgentBugWriteService service = new AgentBugWriteService();
        ReflectionTestUtils.setField(service, "testAssetVersionService", versionService);
        ReflectionTestUtils.setField(service, "functionalCaseMapper", functionalCaseMapper);
        when(versionService.publish(anyString(), eq("BUG"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(version("bug-version"));
        when(versionService.publish(anyString(), eq("CASE"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(version("case-version"));

        Bug bug = new Bug();
        bug.setId("bug-1");
        bug.setProjectId("project-1");
        bug.setTitle("Checkout failed");
        bug.setNum(1001);
        bug.setStatus("NEW");
        bug.setUpdateTime(123L);
        AgentBugCreateRequest request = new AgentBugCreateRequest();
        request.setCaseId("case-row-1");
        request.setDescription("actual result differs");

        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId("case-row-1");
        functionalCase.setRefId("case-stable-1");
        functionalCase.setVersionId("functional-version-1");
        functionalCase.setProjectId("project-1");
        when(functionalCaseMapper.selectByPrimaryKey("case-row-1")).thenReturn(functionalCase);

        service.publishBugRelations(bug, request, "user-1");

        verify(versionService).relate(eq("project-1"), eq("REPORTS"), eq("CASE"), eq("case-stable-1"),
                eq("case-version"), eq("BUG"), eq("bug-1"), eq("bug-version"), anyString(), eq("user-1"));
    }

    private TestAssetVersionDTO version(String id) {
        TestAssetVersionDTO value = new TestAssetVersionDTO();
        value.setId(id);
        return value;
    }
}
