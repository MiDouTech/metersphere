package io.metersphere.functional.service;

import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.request.FunctionalCaseExportRequest;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.sdk.exception.MSException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FunctionalCaseExportValidationTests {
    @Test void everySelectedCaseMustBelongToTheRequestedProject() {
        var cases = mock(FunctionalCaseService.class);
        var files = new FunctionalCaseFileService();
        ReflectionTestUtils.setField(files, "functionalCaseService", cases);
        var request = new FunctionalCaseExportRequest(); request.setProjectId("allowed");
        when(cases.doSelectIds(request,"allowed")).thenReturn(List.of("local","foreign"));
        var local = new FunctionalCase(); local.setId("local"); local.setProjectId("allowed"); local.setDeleted(false);
        var foreign = new FunctionalCase(); foreign.setId("foreign"); foreign.setProjectId("other"); foreign.setDeleted(false);
        when(cases.getCaseDataByIds(List.of("local","foreign"))).thenReturn(List.of(local,foreign));
        assertThrows(MSException.class, () -> files.validatedExportIds(request));
        foreign.setProjectId("allowed"); foreign.setDeleted(true);
        assertThrows(MSException.class, () -> files.validatedExportIds(request));
        when(cases.getCaseDataByIds(List.of("local","foreign"))).thenReturn(List.of(local));
        assertThrows(MSException.class, () -> files.validatedExportIds(request));
    }

    @Test void emptyRangeHasExplicitDomainOutcome() {
        var cases = mock(FunctionalCaseService.class);
        var files = new FunctionalCaseFileService();
        ReflectionTestUtils.setField(files,"functionalCaseService",cases);
        var request = new FunctionalCaseExportRequest(); request.setProjectId("p1");
        when(cases.doSelectIds(request,"p1")).thenReturn(List.of());
        assertThrows(FunctionalCaseExportTaskService.EmptyExportException.class, () -> files.validatedExportIds(request));
    }

    @Test void missingOrDisabledProjectIsNeverAuthorized() {
        var projects = mock(ProjectMapper.class);
        var tasks = new FunctionalCaseExportTaskService();
        ReflectionTestUtils.setField(tasks,"projectMapper",projects);
        try {
            assertThrows(MSException.class, () -> tasks.authorize("missing","u1"));
            var project = new io.metersphere.project.domain.Project(); project.setEnable(false);
            when(projects.selectByPrimaryKey("disabled")).thenReturn(project);
            assertThrows(MSException.class, () -> tasks.authorize("disabled","u1"));
            assertThrows(MSException.class, () -> tasks.authorize("p1",null));
        } finally { tasks.close(); }
    }
}
