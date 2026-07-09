package io.metersphere.agent.mapper;

import io.metersphere.agent.constants.AgentWarningCode;
import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.functional.constants.FunctionalCaseTypeConstants;
import io.metersphere.functional.dto.FunctionalCaseCustomFieldDTO;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.dto.FunctionalCaseStepDTO;
import io.metersphere.plan.dto.response.TestPlanCasePageResponse;
import io.metersphere.sdk.util.JSON;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class AgentCaseSchemaMapperTests {

    private final AgentCaseSchemaMapper mapper = new AgentCaseSchemaMapper();

    @Test
    void fromTestPlanCaseShouldExposeTestPlanCaseId() {
        TestPlanCasePageResponse source = new TestPlanCasePageResponse();
        source.setId("relate-001");
        source.setCaseId("fc-001");
        source.setTestPlanId("plan-001");
        source.setName("下单流程");

        var dto = mapper.fromTestPlanCase(source, "订单/下单流程");
        Assertions.assertEquals("relate-001", dto.getTestPlanCaseId());
        Assertions.assertEquals("fc-001", dto.getCaseId());
        Assertions.assertEquals("plan-001", dto.getTestPlanId());
    }

    @Test
    void textModeShouldGenerateVirtualStepAndWarning() {
        FunctionalCaseDetailDTO detail = new FunctionalCaseDetailDTO();
        detail.setCaseEditType(FunctionalCaseTypeConstants.CaseEditType.TEXT.name());
        detail.setTextDescription("输入账号密码并登录");
        detail.setExpectedResult("进入首页");

        List<String> warnings = new ArrayList<>();
        List<AgentCaseStepDTO> steps = mapper.buildSteps(detail, warnings);

        Assertions.assertEquals(1, steps.size());
        Assertions.assertEquals("输入账号密码并登录", steps.get(0).getDesc());
        Assertions.assertTrue(warnings.contains(AgentWarningCode.TEXT_MODE_CONVERTED));
    }

    @Test
    void stepModeShouldMapExpectedAndActualResult() {
        FunctionalCaseStepDTO step = new FunctionalCaseStepDTO();
        step.setId("step-1");
        step.setNum(1);
        step.setDesc("点击登录");
        step.setResult("登录成功");
        step.setActualResult("已登录");
        step.setExecuteResult("SUCCESS");

        FunctionalCaseDetailDTO detail = new FunctionalCaseDetailDTO();
        detail.setCaseEditType(FunctionalCaseTypeConstants.CaseEditType.STEP.name());
        detail.setSteps(JSON.toJSONString(List.of(step)));

        List<AgentCaseStepDTO> steps = mapper.buildSteps(detail, new ArrayList<>());
        Assertions.assertEquals("登录成功", steps.get(0).getExpected());
        Assertions.assertEquals("已登录", steps.get(0).getActualResult());
    }

    @Test
    void toStepsExecResultJsonShouldBeCompatibleWithRunRequest() {
        AgentCaseStepDTO step = new AgentCaseStepDTO();
        step.setId("step-1");
        step.setNum(1);
        step.setDesc("执行");
        step.setExpected("成功");
        step.setActualResult("通过");
        step.setExecuteResult("SUCCESS");

        String json = mapper.toStepsExecResultJson(List.of(step));
        List<FunctionalCaseStepDTO> parsed = JSON.parseArray(json, FunctionalCaseStepDTO.class);

        Assertions.assertEquals(1, parsed.size());
        Assertions.assertEquals("成功", parsed.get(0).getResult());
        Assertions.assertEquals("通过", parsed.get(0).getActualResult());
    }

    @Test
    void extractPriorityFromCustomField() {
        FunctionalCaseCustomFieldDTO field = new FunctionalCaseCustomFieldDTO();
        field.setFieldName("functional_priority");
        field.setDefaultValue("\"P0\"");

        String priority = mapper.extractPriority(List.of(field), null);
        Assertions.assertEquals("P0", priority);
    }
}
