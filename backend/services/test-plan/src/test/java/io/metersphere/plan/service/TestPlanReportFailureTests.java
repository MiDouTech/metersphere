package io.metersphere.plan.service;

import io.metersphere.plan.dto.request.TestPlanReportGenRequest;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TestPlanReportFailureTests {
    private final TestPlanReportService service=new TestPlanReportService();
    private final TestPlanMapper plans=mock(TestPlanMapper.class);
    private final TestPlanReportGenRequest request=new TestPlanReportGenRequest();

    TestPlanReportFailureTests() {
        ReflectionTestUtils.setField(service,"testPlanMapper",plans);
        request.setTestPlanId("plan");request.setProjectId("project");
    }

    private void generate() {
        ReflectionTestUtils.invokeMethod(service,"genReport","report",null,request,true,"user",null,"AUTO");
    }

    @Test void missingPlanCannotBecomeAnEmptySuccessfulReport() {
        try(var text=mockStatic(Translator.class)) {
            text.when(()->Translator.get("test_plan_not_exist")).thenReturn("missing plan");
            assertEquals("missing plan", assertThrows(MSException.class,this::generate).getMessage());
        }
    }

    @Test void unexpectedFailureIsPropagatedWithoutExposingItsTechnicalMessage() {
        when(plans.selectByPrimaryKey("plan")).thenThrow(new IllegalStateException("sensitive database detail"));
        var error=assertThrows(MSException.class,this::generate);
        assertEquals("报告生成失败，请稍后重试或联系管理员",error.getMessage());
        assertFalse(error.getMessage().contains("sensitive"));
    }
}
