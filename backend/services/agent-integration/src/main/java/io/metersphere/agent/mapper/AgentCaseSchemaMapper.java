package io.metersphere.agent.mapper;

import io.metersphere.agent.constants.AgentConstants;
import io.metersphere.agent.constants.AgentWarningCode;
import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.dto.AgentCaseStepDTO;
import io.metersphere.agent.dto.AgentModuleDTO;
import io.metersphere.agent.resolver.ModuleTreeMatcher;
import io.metersphere.functional.constants.FunctionalCaseTypeConstants;
import io.metersphere.functional.dto.FunctionalCaseCustomFieldDTO;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.dto.FunctionalCasePageDTO;
import io.metersphere.functional.dto.FunctionalCaseStepDTO;
import io.metersphere.plan.dto.response.TestPlanCasePageResponse;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class AgentCaseSchemaMapper {

    public AgentCaseDTO fromFunctionalCasePage(FunctionalCasePageDTO source, String modulePath) {
        AgentCaseDTO target = new AgentCaseDTO();
        target.setCaseId(source.getId());
        target.setNum(source.getNum());
        target.setName(source.getName());
        target.setModulePath(modulePath);
        target.setCaseEditType(source.getCaseEditType());
        target.setTags(source.getTags());
        target.setPriority(extractPriority(source.getCustomFields(), null));
        target.setLastExecuteResult(source.getLastExecuteResult());
        return target;
    }

    public AgentCaseDTO fromTestPlanCase(TestPlanCasePageResponse source, String modulePath) {
        AgentCaseDTO target = new AgentCaseDTO();
        target.setCaseId(source.getCaseId());
        target.setNum(source.getNum());
        target.setName(source.getName());
        target.setModulePath(modulePath);
        target.setTags(source.getTags());
        target.setPriority(extractPriority(source.getCustomFields(), null));
        target.setTestPlanId(source.getTestPlanId());
        target.setTestPlanCaseId(source.getId());
        target.setLastExecuteResult(source.getLastExecResult());
        return target;
    }

    public void enrichDetail(AgentCaseDTO target, FunctionalCaseDetailDTO detail, List<String> warnings) {
        target.setCaseEditType(detail.getCaseEditType());
        target.setPrerequisite(detail.getPrerequisite());
        target.setPriority(StringUtils.defaultIfBlank(detail.getFunctionalPriority(), target.getPriority()));
        if (StringUtils.isBlank(target.getLastExecuteResult())) {
            target.setLastExecuteResult(detail.getLastExecuteResult());
        }
        target.setSteps(buildSteps(detail, warnings));
    }

    public List<AgentCaseStepDTO> buildSteps(FunctionalCaseDetailDTO detail, List<String> warnings) {
        if (StringUtils.equalsIgnoreCase(detail.getCaseEditType(), FunctionalCaseTypeConstants.CaseEditType.TEXT.name())) {
            AgentCaseStepDTO step = new AgentCaseStepDTO();
            step.setNum(1);
            step.setDesc(detail.getTextDescription());
            step.setExpected(detail.getExpectedResult());
            if (warnings != null) {
                warnings.add(AgentWarningCode.TEXT_MODE_CONVERTED);
            }
            return Collections.singletonList(step);
        }
        if (StringUtils.isBlank(detail.getSteps())) {
            return new ArrayList<>();
        }
        List<FunctionalCaseStepDTO> steps = JSON.parseArray(detail.getSteps(), FunctionalCaseStepDTO.class);
        if (CollectionUtils.isEmpty(steps)) {
            return new ArrayList<>();
        }
        return steps.stream().map(this::toAgentStep).collect(Collectors.toList());
    }

    public AgentCaseStepDTO toAgentStep(FunctionalCaseStepDTO source) {
        AgentCaseStepDTO target = new AgentCaseStepDTO();
        target.setId(source.getId());
        target.setNum(source.getNum());
        target.setDesc(source.getDesc());
        target.setExpected(source.getResult());
        target.setActualResult(source.getActualResult());
        target.setExecuteResult(source.getExecuteResult());
        return target;
    }

    public List<FunctionalCaseStepDTO> toFunctionalCaseSteps(List<AgentCaseStepDTO> steps) {
        if (CollectionUtils.isEmpty(steps)) {
            return new ArrayList<>();
        }
        return steps.stream().map(step -> {
            FunctionalCaseStepDTO target = new FunctionalCaseStepDTO();
            target.setId(step.getId());
            target.setNum(step.getNum());
            target.setDesc(step.getDesc());
            target.setResult(step.getExpected());
            target.setActualResult(step.getActualResult());
            target.setExecuteResult(step.getExecuteResult());
            return target;
        }).collect(Collectors.toList());
    }

    public String toStepsExecResultJson(List<AgentCaseStepDTO> steps) {
        return JSON.toJSONString(toFunctionalCaseSteps(steps));
    }

    public AgentModuleDTO toModuleDto(ModuleTreeMatcher.AgentModuleNode node) {
        AgentModuleDTO dto = new AgentModuleDTO();
        dto.setId(node.getId());
        dto.setName(node.getName());
        dto.setPath(node.getPath());
        dto.setParentId(node.getParentId());
        return dto;
    }

    public String extractPriority(List<FunctionalCaseCustomFieldDTO> customFields, String fallback) {
        if (CollectionUtils.isNotEmpty(customFields)) {
            for (FunctionalCaseCustomFieldDTO field : customFields) {
                if (StringUtils.equals(field.getFieldName(), AgentConstants.PRIORITY_FIELD)
                        || StringUtils.equalsIgnoreCase(field.getFieldName(), AgentConstants.PRIORITY_FIELD)) {
                    return normalizeCustomFieldValue(field.getDefaultValue());
                }
            }
        }
        return fallback;
    }

    private String normalizeCustomFieldValue(String value) {
        if (StringUtils.isBlank(value)) {
            return value;
        }
        return StringUtils.remove(value, '"');
    }

    public boolean matchesTags(List<String> tags, List<String> requiredTags) {
        if (CollectionUtils.isEmpty(requiredTags)) {
            return true;
        }
        if (CollectionUtils.isEmpty(tags)) {
            return false;
        }
        List<String> normalized = tags.stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        return requiredTags.stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::contains);
    }

    public boolean matchesPriority(String priority, List<String> priorities) {
        if (CollectionUtils.isEmpty(priorities)) {
            return true;
        }
        return priorities.contains(priority);
    }
}
