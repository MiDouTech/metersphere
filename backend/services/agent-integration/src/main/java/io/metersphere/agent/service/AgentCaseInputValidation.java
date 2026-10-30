package io.metersphere.agent.service;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.dto.sdk.TemplateDTO;
import org.apache.commons.lang3.StringUtils;

/** Shared MCP/HTTP invariants; validate before building a persistence request. */
final class AgentCaseInputValidation {
    private AgentCaseInputValidation() { }

    static void name(String name) {
        if (StringUtils.isBlank(name) || name.length() > 255) throw new MSException("CASE_NAME_INVALID");
    }

    static void template(TemplateDTO template, String requestedId, String projectId) {
        if (template == null || !requestedId.equals(template.getId())
                || !projectId.equals(template.getScopeId()) || !"FUNCTIONAL".equals(template.getScene()))
            throw new MSException("CASE_TEMPLATE_INVALID");
    }

    static String priority(TemplateDTO template, String supplied) {
        String priority=StringUtils.defaultIfBlank(supplied,"P0");
        if (template == null || template.getCustomFields() == null) throw new MSException("CASE_PRIORITY_INVALID");
        var field=template.getCustomFields().stream()
                .filter(f->"functional_priority".equalsIgnoreCase(f.getInternalFieldKey())).findFirst();
        if (field.isEmpty() || field.get().getOptions() == null
                || field.get().getOptions().stream().noneMatch(o->priority.equals(o.getValue())))
            throw new MSException("CASE_PRIORITY_INVALID");
        return priority;
    }
}
