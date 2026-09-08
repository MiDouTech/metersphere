package io.metersphere.functional.service;

import io.metersphere.functional.mapper.ExtFunctionalTestReportMapper;
import io.metersphere.functional.mapper.FunctionalTestReportMapper;
import io.metersphere.functional.request.FunctionalTestReportGenerateRequest;
import io.metersphere.system.event.TestReportGeneratedEvent;
import io.metersphere.system.mapper.UserMapper;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FunctionalTestReportServiceTests {

    @Test
    void oneClickGenerationPublishesAManualFunctionalReportEvent() {
        FunctionalTestReportMapper reportMapper = mock(FunctionalTestReportMapper.class);
        ExtFunctionalTestReportMapper extMapper = mock(ExtFunctionalTestReportMapper.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        FunctionalTestReportService service = new FunctionalTestReportService();
        ReflectionTestUtils.setField(service, "functionalTestReportMapper", reportMapper);
        ReflectionTestUtils.setField(service, "extFunctionalTestReportMapper", extMapper);
        ReflectionTestUtils.setField(service, "userMapper", mock(UserMapper.class));
        ReflectionTestUtils.setField(service, "applicationEventPublisher", events);
        when(extMapper.countExecByPlan("plan-1")).thenReturn(List.of());
        when(extMapper.listRiskCasesByPlan("plan-1")).thenReturn(List.of());
        when(extMapper.listOpenBugsByPlan("plan-1")).thenReturn(List.of());
        when(extMapper.countBugHandlerStatusByPlan("plan-1")).thenReturn(List.of());
        when(extMapper.countBugTypeByPlan("plan-1")).thenReturn(List.of());
        FunctionalTestReportGenerateRequest request = new FunctionalTestReportGenerateRequest();
        request.setProjectId("project-1");
        request.setPlanId("plan-1");
        request.setName("report");

        try (MockedStatic<SessionUtils> session = mockStatic(SessionUtils.class);
             MockedStatic<IDGenerator> ids = mockStatic(IDGenerator.class)) {
            session.when(SessionUtils::getUserId).thenReturn("user-1");
            ids.when(IDGenerator::nextStr).thenReturn("report-1");
            service.generate(request);
        }

        ArgumentCaptor<TestReportGeneratedEvent> event = ArgumentCaptor.forClass(TestReportGeneratedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals("report", event.getValue().reportName());
        assertEquals("report-1", event.getValue().reportId());
        assertEquals("MANUAL", event.getValue().generationMode());
        assertEquals(TestReportGeneratedEvent.TYPE_FUNCTIONAL, event.getValue().reportType());
    }
}
