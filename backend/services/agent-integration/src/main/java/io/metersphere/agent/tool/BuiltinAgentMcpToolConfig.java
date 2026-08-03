package io.metersphere.agent.tool;

import io.metersphere.agent.constants.AgentTokenScope;
import io.metersphere.agent.dto.AgentBugCreateRequest;
import io.metersphere.agent.dto.AgentBugSearchRequest;
import io.metersphere.agent.dto.AgentBugUpdateRequest;
import io.metersphere.agent.dto.AgentCaseBatchCreateRequest;
import io.metersphere.agent.dto.AgentCaseCreateRequest;
import io.metersphere.agent.dto.AgentCaseReviewAssociateRequest;
import io.metersphere.agent.dto.AgentCaseReviewCreateRequest;
import io.metersphere.agent.dto.AgentCaseSearchRequest;
import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.dto.AgentModuleCreateRequest;
import io.metersphere.agent.dto.AgentProjectAddMembersRequest;
import io.metersphere.agent.dto.AgentProjectCreateRequest;
import io.metersphere.agent.dto.AgentProjectSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanAssociateRequest;
import io.metersphere.agent.dto.AgentTestPlanCreateRequest;
import io.metersphere.agent.service.AgentBugWriteService;
import io.metersphere.agent.service.AgentCaseReviewWriteService;
import io.metersphere.agent.service.AgentCaseWriteService;
import io.metersphere.agent.service.AgentFunctionalCaseSearchService;
import io.metersphere.agent.service.AgentFunctionalCaseSubmitService;
import io.metersphere.agent.service.AgentProjectService;
import io.metersphere.agent.service.AgentTestPlanWriteService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 将原 switch 内置 Tool 注册为统一 Handler Bean。
 */
@Configuration
public class BuiltinAgentMcpToolConfig {

    @Bean
    public AgentMcpToolHandler functionalSearchTool(AgentFunctionalCaseSearchService service) {
        return tool("metersphere.functional.search",
                "Search functional test cases. Optional projectId accepts internal project id, UI project number, or exact project name; when omitted it uses Agent Token current/default project.",
                AgentTokenScope.FUNCTIONAL_READ, true,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.search(convert(args, AgentCaseSearchRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler functionalGetTool(AgentFunctionalCaseSearchService service) {
        return tool("metersphere.functional.get", "Get functional test case detail", AgentTokenScope.FUNCTIONAL_READ, true,
                objectSchema(Map.of("caseId", stringSchema(), "includeSteps", Map.of("type", "boolean"), "testPlanId", stringSchema()), List.of("caseId")),
                args -> {
                    boolean includeSteps = !args.containsKey("includeSteps")
                            || BooleanUtils.toBoolean(String.valueOf(args.get("includeSteps")));
                    return service.getById(requiredString(args, "caseId"), includeSteps, (String) args.get("testPlanId"));
                });
    }

    @Bean
    public AgentMcpToolHandler functionalModulesTool(AgentFunctionalCaseSearchService service) {
        return tool("metersphere.functional.modules",
                "List functional case modules by project. projectId accepts internal project id, UI project number, or exact project name.",
                AgentTokenScope.FUNCTIONAL_READ, true,
                objectSchema(Map.of("projectId", stringSchema()), List.of("projectId")),
                args -> service.listModules(requiredString(args, "projectId")));
    }

    @Bean
    public AgentMcpToolHandler functionalSubmitTool(AgentFunctionalCaseSubmitService service) {
        return tool("metersphere.functional.submit",
                "Submit functional case execution result. projectId accepts internal project id, UI project number, or exact project name.",
                AgentTokenScope.FUNCTIONAL_SUBMIT, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> {
                    service.submit(convert(args, AgentCaseSubmitRequest.class));
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler functionalModuleCreateTool(AgentCaseWriteService service) {
        return tool("metersphere.functional.module.create",
                "Create functional case module. projectId accepts internal project id, UI project number, or exact project name.",
                AgentTokenScope.CASE_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.createModule(convert(args, AgentModuleCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler functionalCaseCreateTool(AgentCaseWriteService service) {
        return tool("metersphere.functional.case.create",
                "Create functional case. projectId accepts internal project id, UI project number, or exact project name.",
                AgentTokenScope.CASE_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.createCase(convert(args, AgentCaseCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler functionalCaseBatchCreateTool(AgentCaseWriteService service) {
        return tool("metersphere.functional.case.batch_create",
                "Batch create functional cases. projectId accepts internal project id, UI project number, or exact project name.",
                AgentTokenScope.CASE_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.batchCreate(convert(args, AgentCaseBatchCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler bugSearchTool(AgentBugWriteService service) {
        return tool("metersphere.bug.search", "Search bugs", AgentTokenScope.BUG_READ, true,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.search(convert(args, AgentBugSearchRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler bugGetTool(AgentBugWriteService service) {
        return tool("metersphere.bug.get", "Get bug detail", AgentTokenScope.BUG_READ, true,
                objectSchema(Map.of("bugId", stringSchema()), List.of("bugId")),
                args -> service.get(requiredString(args, "bugId")));
    }

    @Bean
    public AgentMcpToolHandler bugCreateTool(AgentBugWriteService service) {
        return tool("metersphere.bug.create", "Create bug", AgentTokenScope.BUG_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.create(convert(args, AgentBugCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler bugUpdateTool(AgentBugWriteService service) {
        return tool("metersphere.bug.update", "Update bug", AgentTokenScope.BUG_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.update(convert(args, AgentBugUpdateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler projectCreateTool(AgentProjectService service) {
        return tool("metersphere.project.create", "Create project", AgentTokenScope.PROJECT_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.create(convert(args, AgentProjectCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler projectMembersAddTool(AgentProjectService service) {
        return tool("metersphere.project.members.add", "Add project members", AgentTokenScope.PROJECT_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> {
                    service.addMembers(convert(args, AgentProjectAddMembersRequest.class));
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler projectSearchTool(AgentProjectService service) {
        return tool("metersphere.project.search",
                "Search projects by internal project id, project name, or project number. Supports page/pageSize/includeArchived; returns items/total/hasMore.",
                AgentTokenScope.PROJECT_READ, true,
                objectSchema(Map.of(
                        "keyword", stringSchema(),
                        "page", Map.of("type", "integer", "minimum", 1),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 200),
                        "includeArchived", Map.of("type", "boolean")
                ), List.of()),
                args -> service.search(convert(args, AgentProjectSearchRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler projectListTool(AgentProjectService service) {
        return tool("metersphere.project.list",
                "List projects accessible to the current Agent Token user. Same contract as metersphere.project.search.",
                AgentTokenScope.PROJECT_READ, true,
                objectSchema(Map.of(
                        "keyword", stringSchema(),
                        "page", Map.of("type", "integer", "minimum", 1),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 200),
                        "includeArchived", Map.of("type", "boolean")
                ), List.of()),
                args -> service.search(convert(args, AgentProjectSearchRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler projectGetTool(AgentProjectService service) {
        return tool("metersphere.project.get", "Get project detail", AgentTokenScope.PROJECT_READ, true,
                objectSchema(Map.of("projectId", stringSchema()), List.of("projectId")),
                args -> service.get(requiredString(args, "projectId")));
    }

    @Bean
    public AgentMcpToolHandler testPlanCreateTool(AgentTestPlanWriteService service) {
        return tool("metersphere.test_plan.create", "Create test plan", AgentTokenScope.PLAN_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.create(convert(args, AgentTestPlanCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler testPlanAssociateTool(AgentTestPlanWriteService service) {
        return tool("metersphere.test_plan.associate_cases", "Associate cases to test plan", AgentTokenScope.PLAN_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> {
                    service.associate(convert(args, AgentTestPlanAssociateRequest.class));
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler testPlanGetTool(AgentTestPlanWriteService service) {
        return tool("metersphere.test_plan.get", "Get test plan detail", AgentTokenScope.FUNCTIONAL_READ, true,
                objectSchema(Map.of("testPlanId", stringSchema()), List.of("testPlanId")),
                args -> service.get(requiredString(args, "testPlanId")));
    }

    @Bean
    public AgentMcpToolHandler caseReviewCreateTool(AgentCaseReviewWriteService service) {
        return tool("metersphere.case_review.create", "Create case review", AgentTokenScope.REVIEW_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> service.create(convert(args, AgentCaseReviewCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler caseReviewAssociateTool(AgentCaseReviewWriteService service) {
        return tool("metersphere.case_review.associate_cases", "Associate cases to review", AgentTokenScope.REVIEW_WRITE, false,
                Map.of("type", "object", "additionalProperties", true),
                args -> {
                    service.associate(convert(args, AgentCaseReviewAssociateRequest.class));
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler caseReviewGetTool(AgentCaseReviewWriteService service) {
        return tool("metersphere.case_review.get", "Get case review detail", AgentTokenScope.FUNCTIONAL_READ, true,
                objectSchema(Map.of("reviewId", stringSchema()), List.of("reviewId")),
                args -> service.get(requiredString(args, "reviewId")));
    }

    private static AgentMcpToolHandler tool(String name, String description, String scope, boolean readOnly,
                                            Map<String, Object> inputSchema, Function<Map<String, Object>, Object> executor) {
        Map<String, Object> annotations = new LinkedHashMap<>();
        annotations.put("scope", scope);
        annotations.put("readOnlyHint", readOnly);
        return new AgentMcpToolHandler() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description;
            }

            @Override
            public String requiredScope() {
                return scope;
            }

            @Override
            public Map<String, Object> inputSchema() {
                return inputSchema;
            }

            @Override
            public Map<String, Object> annotations() {
                return annotations;
            }

            @Override
            public Object execute(Map<String, Object> arguments) {
                return executor.apply(arguments == null ? Map.of() : arguments);
            }
        };
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required);
    }

    private static Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private static <T> T convert(Map<String, Object> arguments, Class<T> clazz) {
        return JSON.parseObject(JSON.toJSONString(arguments), clazz);
    }

    private static String requiredString(Map<String, Object> arguments, String key) {
        String value = (String) arguments.get(key);
        if (StringUtils.isBlank(value)) {
            throw new MSException("Missing required argument: " + key);
        }
        return value;
    }
}
