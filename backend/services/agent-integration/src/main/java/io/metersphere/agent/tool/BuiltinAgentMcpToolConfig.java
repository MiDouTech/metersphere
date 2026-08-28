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
import io.metersphere.agent.dto.AgentExecutionCreateRequest;
import io.metersphere.agent.dto.AgentExecutionPreflightRequest;
import io.metersphere.agent.constants.AgentTaskOrigin;
import io.metersphere.agent.dto.AgentExecutionEventsRequest;
import io.metersphere.agent.dto.AgentExecutionResolveRequest;
import io.metersphere.agent.dto.AgentModuleCreateRequest;
import io.metersphere.agent.dto.AgentProjectAddMembersRequest;
import io.metersphere.agent.dto.AgentProjectCreateRequest;
import io.metersphere.agent.dto.AgentProjectSearchRequest;
import io.metersphere.agent.dto.AgentTestPlanAssociateRequest;
import io.metersphere.agent.dto.AgentTestPlanCreateRequest;
import io.metersphere.agent.dto.AgentTestPlanSearchRequest;
import io.metersphere.agent.dto.AgentTaskTriggerRequest;
import io.metersphere.agent.dto.AgentTaskClaimRequest;
import io.metersphere.agent.dto.AgentRunnerEventsRequest;
import io.metersphere.agent.dto.AgentRunnerLeaseCompleteRequest;
import io.metersphere.agent.dto.AgentHumanCreateRequest;
import io.metersphere.agent.dto.AgentArtifactPrepareRequest;
import io.metersphere.agent.dto.AgentArtifactCommitRequest;
import io.metersphere.agent.dto.AgentExecutionStepSubmitRequest;
import io.metersphere.agent.dto.AgentCheckpointCreateRequest;
import io.metersphere.agent.dto.AgentHumanResponseRequest;
import io.metersphere.agent.security.AgentTokenContext;
import io.metersphere.agent.service.AgentExecutionArtifactService;
import io.metersphere.agent.service.AgentBugWriteService;
import io.metersphere.agent.service.AgentCaseReviewWriteService;
import io.metersphere.agent.service.AgentCaseWriteService;
import io.metersphere.agent.service.AgentExecutionService;
import io.metersphere.agent.service.AgentExecutionPreflightService;
import io.metersphere.agent.service.TestAssetCatalogService;
import io.metersphere.agent.service.AgentEnvironmentProfileService;
import io.metersphere.agent.service.AgentCredentialReferenceService;
import io.metersphere.agent.service.AgentPageObjectService;
import io.metersphere.agent.service.AgentBusinessFlowService;
import io.metersphere.agent.service.AgentFunctionalCaseSearchService;
import io.metersphere.agent.service.AgentFunctionalCaseSubmitService;
import io.metersphere.agent.service.AgentProjectService;
import io.metersphere.agent.service.AgentTestPlanQueryService;
import io.metersphere.agent.service.AgentTestPlanWriteService;
import io.metersphere.agent.service.AgentTaskTriggerService;
import io.metersphere.agent.service.AgentTaskClaimService;
import io.metersphere.agent.service.AgentTaskExecutionApplicationService;
import io.metersphere.agent.service.AgentHumanRequestService;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collection;
import java.util.function.Function;
import io.metersphere.system.utils.Pager;
import java.util.UUID;

/**
 * 将原 switch 内置 Tool 注册为统一 Handler Bean。
 */
@Configuration
public class BuiltinAgentMcpToolConfig {

    @Bean
    public AgentMcpToolHandler assetCatalogSearchTool(TestAssetCatalogService service) {
        return tool("metersphere.asset.catalog.search","Search published test-asset catalog metadata.",AgentTokenScope.AI_ASSET_READ,true,
                objectSchema(Map.of("projectId",stringSchema(),"assetTypes",Map.of("type","array","minItems",1,"maxItems",11,"uniqueItems",true,"items",Map.of("type","string","enum",List.of("CASE","DOCUMENT","PLAN","DATASET","ENVIRONMENT","PAGE_OBJECT","BUSINESS_FLOW","COMMON_STEP","API_DEFINITION","EVIDENCE","BUG"))),"keyword",stringSchema(),"status",Map.of("type","string","enum",List.of("PUBLISHED")),"cursor",stringSchema(),"limit",Map.of("type","integer","minimum",1,"maximum",100)),List.of("projectId","assetTypes")),
                args->pageResponse(service.searchByTypes(requiredString(args,"projectId"),stringList(args,"assetTypes"),(String)args.get("keyword"),(String)args.get("status"),cursorPage(args),optionalInt(args,"limit",20))));
    }
    @Bean public AgentMcpToolHandler assetGetTool(TestAssetCatalogService service){return tool("metersphere.asset.get","Get test-asset metadata or one immutable version.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema(),"assetType",stringSchema(),"assetId",stringSchema(),"versionId",stringSchema()),List.of("projectId","assetType","assetId")),args->{String projectId=requiredString(args,"projectId"),assetType=requiredString(args,"assetType"),assetId=requiredString(args,"assetId"),versionId=(String)args.get("versionId");if(StringUtils.isBlank(versionId))return service.detail(projectId,assetType,assetId);var version=service.version(projectId,versionId);if(!assetType.equalsIgnoreCase(version.getAssetType())||!assetId.equals(version.getAssetId()))throw new MSException("ASSET_VERSION_MISMATCH");return version;});}
    @Bean public AgentMcpToolHandler assetVersionGetTool(TestAssetCatalogService service){return tool("metersphere.asset.version.get","Get one immutable asset version.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema(),"versionId",stringSchema()),List.of("projectId","versionId")),args->service.version(requiredString(args,"projectId"),requiredString(args,"versionId")));}
    @Bean public AgentMcpToolHandler assetRelationListTool(TestAssetCatalogService service){return tool("metersphere.asset.relation.list","List asset relations.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema(),"assetType",stringSchema(),"assetId",stringSchema(),"relationType",stringSchema(),"keyword",stringSchema(),"cursor",stringSchema(),"limit",Map.of("type","integer","minimum",1,"maximum",100)),List.of("projectId")),args->pageResponse(service.relations(requiredString(args,"projectId"),(String)args.get("assetType"),(String)args.get("assetId"),(String)args.get("relationType"),(String)args.get("keyword"),cursorPage(args),optionalInt(args,"limit",20))));}
    @Bean public AgentMcpToolHandler environmentProfileListTool(AgentEnvironmentProfileService service){return tool("metersphere.environment.profile.list","List executable environment profiles.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema()),List.of("projectId")),args->service.list(requiredString(args,"projectId")));}
    @Bean public AgentMcpToolHandler environmentProfileGetTool(AgentEnvironmentProfileService service){return tool("metersphere.environment.profile.get","Get an executable environment profile in the authorized project.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema(),"environmentProfileId",stringSchema()),List.of("projectId","environmentProfileId")),args->service.get(requiredString(args,"projectId"),requiredString(args,"environmentProfileId")));}
    @Bean public AgentMcpToolHandler credentialMetadataListTool(AgentCredentialReferenceService service){return tool("metersphere.credential.metadata.list","List credential metadata for an environment profile without secretRef or secret value.",AgentTokenScope.AI_CREDENTIAL_READ_METADATA,true,objectSchema(Map.of("projectId",stringSchema(),"environmentProfileId",stringSchema(),"businessRole",stringSchema()),List.of("projectId","environmentProfileId")),args->service.listByEnvironmentProfile(requiredString(args,"projectId"),requiredString(args,"environmentProfileId"),(String)args.get("businessRole")));}
    @Bean public AgentMcpToolHandler pageObjectGetTool(AgentPageObjectService service){return tool("metersphere.page_object.get","Get a published governed page object and its semantic locators in the authorized project.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema(),"pageObjectId",stringSchema()),List.of("projectId","pageObjectId")),args->{var page=service.get(requiredString(args,"projectId"),requiredString(args,"pageObjectId"));if(!"PUBLISHED".equals(page.getStatus()))throw new MSException("PUBLISHED_PAGE_OBJECT_REQUIRED");return page;});}
    @Bean public AgentMcpToolHandler businessFlowGetTool(AgentBusinessFlowService service){return tool("metersphere.business_flow.get","Get a published governed business flow in the authorized project.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema(),"businessFlowId",stringSchema()),List.of("projectId","businessFlowId")),args->{var flow=service.get(requiredString(args,"projectId"),requiredString(args,"businessFlowId"));if(!"PUBLISHED".equals(flow.getStatus()))throw new MSException("PUBLISHED_BUSINESS_FLOW_REQUIRED");return flow;});}
    @Bean public AgentMcpToolHandler datasetMetadataGetTool(TestAssetCatalogService service){return tool("metersphere.dataset.metadata.get","Get published dataset metadata without secret data values.",AgentTokenScope.AI_ASSET_READ,true,objectSchema(Map.of("projectId",stringSchema(),"datasetId",stringSchema()),List.of("projectId","datasetId")),args->service.detail(requiredString(args,"projectId"),"DATASET",requiredString(args,"datasetId")));}

    @Bean
    public AgentMcpToolHandler functionalSearchTool(AgentFunctionalCaseSearchService service) {
        return tool("metersphere.functional.search",
                "Search functional test cases. Optional projectId accepts internal project id, UI project number, or exact project name; when omitted it uses Agent Token current/default project.",
                AgentTokenScope.FUNCTIONAL_READ, true,
                dtoSchema(AgentCaseSearchRequest.class),
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
                dtoSchema(AgentCaseSubmitRequest.class),
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
                dtoSchema(AgentModuleCreateRequest.class),
                args -> service.createModule(convert(args, AgentModuleCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler functionalCaseCreateTool(AgentCaseWriteService service) {
        return tool("metersphere.functional.case.create",
                "Create functional case. projectId accepts internal project id, UI project number, or exact project name.",
                AgentTokenScope.CASE_WRITE, false,
                dtoSchema(AgentCaseCreateRequest.class),
                args -> service.createCase(convert(args, AgentCaseCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler functionalCaseBatchCreateTool(AgentCaseWriteService service) {
        return tool("metersphere.functional.case.batch_create",
                "Batch create functional cases. projectId accepts internal project id, UI project number, or exact project name.",
                AgentTokenScope.CASE_WRITE, false,
                dtoSchema(AgentCaseBatchCreateRequest.class),
                args -> service.batchCreate(convert(args, AgentCaseBatchCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler bugSearchTool(AgentBugWriteService service) {
        return tool("metersphere.bug.search", "Search bugs", AgentTokenScope.BUG_READ, true,
                dtoSchema(AgentBugSearchRequest.class),
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
                dtoSchema(AgentBugCreateRequest.class),
                args -> service.create(convert(args, AgentBugCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler bugUpdateTool(AgentBugWriteService service) {
        return tool("metersphere.bug.update", "Update bug", AgentTokenScope.BUG_WRITE, false,
                dtoSchema(AgentBugUpdateRequest.class),
                args -> service.update(convert(args, AgentBugUpdateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler projectCreateTool(AgentProjectService service) {
        return tool("metersphere.project.create", "Create project", AgentTokenScope.PROJECT_WRITE, false,
                dtoSchema(AgentProjectCreateRequest.class),
                args -> service.create(convert(args, AgentProjectCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler projectMembersAddTool(AgentProjectService service) {
        return tool("metersphere.project.members.add", "Add project members", AgentTokenScope.PROJECT_WRITE, false,
                dtoSchema(AgentProjectAddMembersRequest.class),
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
                dtoSchema(AgentTestPlanCreateRequest.class),
                args -> service.create(convert(args, AgentTestPlanCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler testPlanAssociateTool(AgentTestPlanWriteService service) {
        return tool("metersphere.test_plan.associate_cases", "Associate cases to test plan", AgentTokenScope.PLAN_WRITE, false,
                dtoSchema(AgentTestPlanAssociateRequest.class),
                args -> {
                    service.associate(convert(args, AgentTestPlanAssociateRequest.class));
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler testPlanGetTool(AgentTestPlanWriteService service) {
        return tool("metersphere.test_plan.get", "Get test plan detail", AgentTokenScope.PLAN_READ, true,
                objectSchema(Map.of("testPlanId", stringSchema()), List.of("testPlanId")),
                args -> service.get(requiredString(args, "testPlanId")));
    }

    @Bean
    public AgentMcpToolHandler testPlanSearchTool(AgentTestPlanQueryService service) {
        return tool("metersphere.test_plan.search",
                "Search test plans by projectId, keyword, status, includeArchived, page, and pageSize.",
                AgentTokenScope.PLAN_READ, true,
                objectSchema(Map.of(
                        "projectId", stringSchema(),
                        "keyword", stringSchema(),
                        "status", stringSchema(),
                        "includeArchived", Map.of("type", "boolean"),
                        "page", Map.of("type", "integer", "minimum", 1),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100)
                ), List.of("projectId")),
                args -> service.search(convert(args, AgentTestPlanSearchRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler testPlanCasesTool(AgentTestPlanQueryService service) {
        return tool("metersphere.test_plan.cases",
                "List functional cases in a test plan. Requires projectId and testPlanId.",
                AgentTokenScope.FUNCTIONAL_READ, true,
                objectSchema(Map.of(
                        "projectId", stringSchema(),
                        "testPlanId", stringSchema(),
                        "current", Map.of("type", "integer", "minimum", 1),
                        "pageSize", Map.of("type", "integer", "minimum", 1, "maximum", 100),
                        "includeSteps", Map.of("type", "boolean")
                ), List.of("projectId", "testPlanId")),
                args -> service.cases(requiredString(args, "projectId"), requiredString(args, "testPlanId"),
                        optionalInt(args, "current", 1), optionalInt(args, "pageSize", 50),
                        optionalBool(args, "includeSteps", true)));
    }

    @Bean
    public AgentMcpToolHandler executionResolveTool(AgentExecutionService service) {
        return tool("metersphere.execution.resolve",
                "Resolve project, test plan, and functional case execution scope before creating an AI execution task.",
                AgentTokenScope.AI_EXECUTION_READ, true,
                dtoSchema(AgentExecutionResolveRequest.class),
                args -> service.resolve(convert(args, AgentExecutionResolveRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler executionPreflightTool(AgentExecutionPreflightService service) {
        return tool("metersphere.execution.preflight", "Validate and freeze scope, environment, credential and policy before creating a PERSONAL_MCP execution.",
                AgentTokenScope.TASK_CLAIM, false,
                objectSchema(Map.of("projectId", stringSchema(), "caseIds", Map.of("type","array","maxItems",100,"items",stringSchema()),
                        "testPlanId", stringSchema(), "environmentProfileId", stringSchema(), "credentialReferenceId", stringSchema(),
                        "runnerType", stringSchema(), "requiredCapabilities", Map.of("type","array","maxItems",32,"items",stringSchema()),
                        "requestId", stringSchema()), List.of("projectId","environmentProfileId","runnerType","requestId")),
                args -> {AgentExecutionPreflightRequest request=convert(args,AgentExecutionPreflightRequest.class);request.setTaskOrigin(AgentTaskOrigin.PERSONAL_MCP);return service.preflight(request);});
    }

    @Bean
    public AgentMcpToolHandler executionCreateTool(AgentExecutionService service) {
        return tool("metersphere.execution.create",
                "Create an AI execution task. Backend revalidates project, plan, cases, and confirmation constraints.",
                AgentTokenScope.TASK_CLAIM, false,
                dtoSchema(AgentExecutionCreateRequest.class),
                args -> service.createPersonalMcp(convert(args, AgentExecutionCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler taskSearchTool(AgentTaskClaimService service) {
        return tool("metersphere.task.search", "Search PERSONAL_MCP tasks visible to the current token.",
                AgentTokenScope.TASK_READ, true,
                dtoSchema(AgentTaskClaimRequest.class),
                args -> service.search(convert(args, AgentTaskClaimRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler taskGetTool(AgentTaskClaimService service) {
        return tool("metersphere.task.get", "Get a PERSONAL_MCP task and its frozen execution context.",
                AgentTokenScope.TASK_READ, true,
                objectSchema(Map.of("taskId", stringSchema()), List.of("taskId")),
                args -> service.getPersonalTask(requiredString(args, "taskId")));
    }

    @Bean
    public AgentMcpToolHandler taskClaimTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.task.claim", "Atomically claim a PERSONAL_MCP task and receive an execution lease.",
                AgentTokenScope.TASK_CLAIM, false,
                dtoSchema(AgentTaskClaimRequest.class),
                args -> service.claim(convert(args, AgentTaskClaimRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler taskLeaseHeartbeatTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.task.lease.heartbeat", "Renew the current execution lease.",
                AgentTokenScope.TASK_CLAIM, false,
                objectSchema(Map.of("leaseId", stringSchema(), "leaseToken", stringSchema()),
                        List.of("leaseId", "leaseToken")),
                args -> {
                    service.heartbeatLease(requiredString(args, "leaseId"), requiredString(args, "leaseToken"));
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler taskReleaseTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.task.release", "Release a claimed task and record a safe reason.",
                AgentTokenScope.TASK_RESULT_WRITE, false,
                objectSchema(Map.of("taskId", stringSchema(), "leaseId", stringSchema(),
                                "leaseToken", stringSchema(), "reason", stringSchema()),
                        List.of("taskId", "leaseId", "leaseToken")),
                args -> {
                    service.release(requiredString(args, "taskId"), requiredString(args, "leaseId"),
                            requiredString(args, "leaseToken"), (String) args.get("reason"));
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler executionEventsBatchTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.execution.events.batch", "Append an ordered, idempotent batch of execution events.",
                AgentTokenScope.TASK_EVENT_WRITE, false,
                dtoSchema(AgentRunnerEventsRequest.class),
                args -> {
                    String leaseId = requiredString(args, "leaseId");
                    AgentRunnerEventsRequest request = convert(args, AgentRunnerEventsRequest.class);
                    request.setLeaseId(leaseId);
                    service.appendEvents(leaseId, requiredString(args, "leaseToken"), request);
                    return Map.of("ok", true);
                });
    }

    @Bean
    public AgentMcpToolHandler executionStepSubmitTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.execution.step.submit", "Submit an idempotent result for one execution step.",
                AgentTokenScope.TASK_RESULT_WRITE, false,
                dtoSchema(AgentExecutionStepSubmitRequest.class),
                args -> service.submitStepResult(convert(args, AgentExecutionStepSubmitRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler executionCompleteTool(AgentTaskExecutionApplicationService service) {
        return terminalExecutionTool("metersphere.execution.complete", "COMPLETED", service);
    }

    @Bean
    public AgentMcpToolHandler executionFailTool(AgentTaskExecutionApplicationService service) {
        return terminalExecutionTool("metersphere.execution.fail", "FAILED", service);
    }

    @Bean
    public AgentMcpToolHandler artifactPrepareTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.artifact.prepare", "Prepare a bounded evidence upload.",
                AgentTokenScope.ARTIFACT_WRITE, false,
                dtoSchema(AgentArtifactPrepareRequest.class),
                args -> service.prepareArtifact(convert(args, AgentArtifactPrepareRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler artifactCommitTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.artifact.commit", "Verify and commit a previously uploaded evidence artifact.",
                AgentTokenScope.ARTIFACT_WRITE, false,
                dtoSchema(AgentArtifactCommitRequest.class),
                args -> service.commitArtifact(convert(args, AgentArtifactCommitRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler humanRequestCreateTool(AgentTaskExecutionApplicationService service) {
        return tool("metersphere.human_request.create", "Request human approval, input, login or review.",
                AgentTokenScope.TASK_RESULT_WRITE, false,
                dtoSchema(AgentHumanCreateRequest.class),
                args -> service.createHumanRequest(requiredString(args, "taskId"), requiredString(args, "leaseId"),
                        requiredString(args, "leaseToken"), convert(args, AgentHumanCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler humanRequestGetTool(AgentHumanRequestService service,
                                                    AgentTaskClaimService taskClaimService) {
        return tool("metersphere.human_request.get", "Read a human intervention request for a task.",
                AgentTokenScope.TASK_READ, true,
                objectSchema(Map.of("taskId", stringSchema(), "requestId", stringSchema()),
                        List.of("taskId", "requestId")),
                args -> {
                    String taskId = requiredString(args, "taskId");
                    String requestId = requiredString(args, "requestId");
                    taskClaimService.getPersonalTask(taskId);
                    return service.list(taskId).stream()
                            .filter(item -> requestId.equals(item.getId()))
                            .findFirst().orElseThrow(() -> new MSException("HUMAN_REQUEST_NOT_FOUND"));
                });
    }

    public AgentMcpToolHandler executionTriggerCreateTool(AgentTaskTriggerService service) {
        return tool("metersphere.execution.trigger.create",
                "Create a CRON, EVENT, or MANUAL execution trigger. The backend validates project access and the frozen task template.",
                AgentTokenScope.PLATFORM_AUTOMATION_MANAGE, false,
                dtoSchema(AgentTaskTriggerRequest.class),
                args -> service.create(convert(args, AgentTaskTriggerRequest.class)));
    }

    public AgentMcpToolHandler executionTriggerUpdateTool(AgentTaskTriggerService service) {
        return tool("metersphere.execution.trigger.update", "Update an existing execution trigger.",
                AgentTokenScope.PLATFORM_AUTOMATION_MANAGE, false,
                Map.of("type", "object", "properties", Map.of(
                        "triggerId", stringSchema(), "request", dtoSchema(AgentTaskTriggerRequest.class)),
                        "required", List.of("triggerId", "request")),
                args -> service.update(requiredString(args, "triggerId"),
                        convert((Map<String, Object>) args.get("request"), AgentTaskTriggerRequest.class)));
    }

    public AgentMcpToolHandler executionTriggerListTool(AgentTaskTriggerService service) {
        return tool("metersphere.execution.trigger.list", "List execution triggers for a project.",
                AgentTokenScope.PLATFORM_AUTOMATION_MANAGE, true,
                objectSchema(Map.of("projectId", stringSchema()), List.of("projectId")),
                args -> service.list(requiredString(args, "projectId")));
    }

    public AgentMcpToolHandler executionTriggerFireTool(AgentTaskTriggerService service) {
        return tool("metersphere.execution.trigger.fire", "Immediately fire an existing execution trigger.",
                AgentTokenScope.PLATFORM_AUTOMATION_MANAGE, false,
                objectSchema(Map.of("triggerId", stringSchema()), List.of("triggerId")),
                args -> service.manualFire(requiredString(args, "triggerId")));
    }

    @Bean
    public AgentMcpToolHandler executionGetTool(AgentExecutionService service,AgentTaskClaimService taskClaims) {
        return tool("metersphere.execution.get", "Get AI execution task detail.",
                AgentTokenScope.AI_EXECUTION_READ, true,
                objectSchema(Map.of("executionTaskId", stringSchema()), List.of("executionTaskId")),
                args -> {String id=requiredString(args,"executionTaskId");taskClaims.getPersonalTask(id);return service.get(id);});
    }

    @Bean
    public AgentMcpToolHandler executionEventsTool(AgentExecutionService service,AgentTaskClaimService taskClaims) {
        return tool("metersphere.execution.events", "Read append-only AI execution events.",
                AgentTokenScope.AI_EXECUTION_READ, true,
                objectSchema(Map.of(
                        "executionTaskId", stringSchema(),
                        "cursor", Map.of("type", "integer", "minimum", 0),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 500)
                ), List.of("executionTaskId")),
                args -> {String id=requiredString(args,"executionTaskId");taskClaims.getPersonalTask(id);return service.events(id, convert(args, AgentExecutionEventsRequest.class));});
    }

    @Bean
    public AgentMcpToolHandler executionCancelTool(AgentExecutionService service,AgentTaskClaimService taskClaims) {
        return tool("metersphere.execution.cancel", "Cancel an AI execution task.",
                AgentTokenScope.AI_EXECUTION_CANCEL, false,
                objectSchema(Map.of("executionTaskId", stringSchema(), "reason", stringSchema()), List.of("executionTaskId")),
                args -> {String id=requiredString(args,"executionTaskId");taskClaims.getPersonalTask(id);return service.cancel(id,(String)args.get("reason"));});
    }

    @Bean
    public AgentMcpToolHandler executionResumeTool(AgentExecutionService service,AgentTaskClaimService taskClaims) {
        return tool("metersphere.execution.resume", "Resume an AI execution task after manual login is ready.",
                AgentTokenScope.AI_EXECUTION_LOGIN, false,
                objectSchema(Map.of("executionTaskId", stringSchema(), "reason", stringSchema()), List.of("executionTaskId")),
                args -> {String id=requiredString(args,"executionTaskId");taskClaims.getPersonalTask(id);return service.loginReady(id,(String)args.get("reason"));});
    }

    @Bean public AgentMcpToolHandler executionPauseTool(AgentExecutionService service,AgentTaskClaimService taskClaims){return tool("metersphere.execution.pause","Pause a PERSONAL_MCP execution.",AgentTokenScope.AI_EXECUTION_RUN,false,objectSchema(Map.of("executionTaskId",stringSchema(),"reason",stringSchema(),"requestId",stringSchema()),List.of("executionTaskId","requestId")),args->{String id=requiredString(args,"executionTaskId");taskClaims.getPersonalTask(id);return service.pause(id,(String)args.get("reason"));});}
    @Bean public AgentMcpToolHandler executionRetryTool(AgentExecutionService service,AgentTaskClaimService taskClaims){return tool("metersphere.execution.retry","Retry a terminal PERSONAL_MCP execution using its frozen scope.",AgentTokenScope.AI_EXECUTION_RUN,false,objectSchema(Map.of("executionTaskId",stringSchema(),"reason",stringSchema(),"requestId",stringSchema()),List.of("executionTaskId","requestId")),args->{String id=requiredString(args,"executionTaskId");taskClaims.getPersonalTask(id);return service.retry(id,(String)args.get("reason"));});}
    @Bean public AgentMcpToolHandler executionCheckpointCreateTool(AgentTaskExecutionApplicationService service){return tool("metersphere.execution.checkpoint.create","Persist a hashed execution checkpoint and release the active lease.",AgentTokenScope.TASK_RESULT_WRITE,false,objectSchema(Map.of("taskId",stringSchema(),"leaseId",stringSchema(),"leaseToken",stringSchema(),"executionId",stringSchema(),"reason",stringSchema(),"stateSnapshot",stringSchema(),"requestId",stringSchema()),List.of("taskId","leaseId","leaseToken","executionId","reason","stateSnapshot","requestId")),args->service.createCheckpoint(requiredString(args,"taskId"),requiredString(args,"leaseId"),requiredString(args,"leaseToken"),convert(args,AgentCheckpointCreateRequest.class)));}
    @Bean public AgentMcpToolHandler humanRequestListTool(AgentHumanRequestService service,AgentTaskClaimService taskClaims){return tool("metersphere.human_request.list","List human requests for a PERSONAL_MCP task.",AgentTokenScope.TASK_READ,true,objectSchema(Map.of("taskId",stringSchema()),List.of("taskId")),args->{String id=requiredString(args,"taskId");taskClaims.getPersonalTask(id);return service.list(id);});}
    @Bean public AgentMcpToolHandler humanRequestRespondTool(AgentHumanRequestService service,AgentTaskClaimService taskClaims){return tool("metersphere.human_request.respond","Respond as the token owner; first valid recipient wins.",AgentTokenScope.AI_EXECUTION_LOGIN,false,objectSchema(Map.of("taskId",stringSchema(),"humanRequestId",stringSchema(),"requestId",stringSchema(),"action",stringSchema(),"response",stringSchema(),"expectedVersion",Map.of("type","integer","minimum",0)),List.of("taskId","humanRequestId","requestId","action","expectedVersion")),args->{String taskId=requiredString(args,"taskId");taskClaims.getPersonalTask(taskId);var token=AgentTokenContext.get();if(token==null)throw new MSException("AUTHENTICATION_REQUIRED");return service.respond(taskId,requiredString(args,"humanRequestId"),convert(args,AgentHumanResponseRequest.class),token.getUserId());});}
    @Bean public AgentMcpToolHandler artifactListTool(AgentExecutionArtifactService service,AgentTaskClaimService taskClaims){return tool("metersphere.artifact.list","List redacted evidence metadata for a PERSONAL_MCP task.",AgentTokenScope.TASK_READ,true,objectSchema(Map.of("taskId",stringSchema()),List.of("taskId")),args->{String id=requiredString(args,"taskId");taskClaims.getPersonalTask(id);return service.list(id);});}
    @Bean public AgentMcpToolHandler executionResultGetTool(AgentExecutionService service,AgentTaskClaimService taskClaims){return tool("metersphere.execution.result.get","Get the current result and verdict for a PERSONAL_MCP task.",AgentTokenScope.TASK_READ,true,objectSchema(Map.of("taskId",stringSchema()),List.of("taskId")),args->{var task=taskClaims.getPersonalTask(requiredString(args,"taskId"));return Map.of("taskId",task.getId(),"status",StringUtils.defaultString(task.getStatus()),"verdict",StringUtils.defaultString(task.getVerdict()),"verdictReason",StringUtils.defaultString(task.getVerdictReason()),"traceId",StringUtils.defaultString(task.getTraceId()));});}
    @Bean public AgentMcpToolHandler writebackStatusGetTool(AgentTaskClaimService taskClaims){return tool("metersphere.writeback.status.get","Get writeback and artifact reconciliation status.",AgentTokenScope.TASK_READ,true,objectSchema(Map.of("taskId",stringSchema()),List.of("taskId")),args->{var task=taskClaims.getPersonalTask(requiredString(args,"taskId"));return Map.of("taskId",task.getId(),"writebackStatus",StringUtils.defaultString(task.getWritebackStatus()),"artifactStatus",StringUtils.defaultString(task.getArtifactStatus()),"traceId",StringUtils.defaultString(task.getTraceId()));});}

    @Bean
    public AgentMcpToolHandler caseReviewCreateTool(AgentCaseReviewWriteService service) {
        return tool("metersphere.case_review.create", "Create case review", AgentTokenScope.REVIEW_WRITE, false,
                dtoSchema(AgentCaseReviewCreateRequest.class),
                args -> service.create(convert(args, AgentCaseReviewCreateRequest.class)));
    }

    @Bean
    public AgentMcpToolHandler caseReviewAssociateTool(AgentCaseReviewWriteService service) {
        return tool("metersphere.case_review.associate_cases", "Associate cases to review", AgentTokenScope.REVIEW_WRITE, false,
                dtoSchema(AgentCaseReviewAssociateRequest.class),
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
        annotations.put("destructiveHint", name.contains(".cancel") || name.contains(".delete") || name.contains(".transition"));
        annotations.put("idempotentHint", true);
        annotations.put("openWorldHint", false);
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

    private static AgentMcpToolHandler terminalExecutionTool(String name, String outcome,
                                                              AgentTaskExecutionApplicationService service) {
        return tool(name, "Submit the terminal execution outcome.", AgentTokenScope.TASK_RESULT_WRITE, false,
                objectSchema(Map.of("leaseId", stringSchema(), "leaseToken", stringSchema(), "reason", stringSchema()),
                        List.of("leaseId", "leaseToken")),
                args -> {
                    AgentRunnerLeaseCompleteRequest request = new AgentRunnerLeaseCompleteRequest();
                    request.setOutcome(outcome);
                    request.setReason((String) args.get("reason"));
                    service.complete(requiredString(args, "leaseId"), requiredString(args, "leaseToken"), request);
                    return Map.of("ok", true);
                });
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }

    private static Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private static int cursorPage(Map<String,Object> args){String cursor=(String)args.get("cursor");if(StringUtils.isBlank(cursor))return 1;try{int page=Integer.parseInt(cursor);if(page<1)throw new NumberFormatException();return page;}catch(NumberFormatException ex){throw new MSException("INVALID_CURSOR");}}
    private static Map<String,Object> pageResponse(Pager<? extends List<?>> page){boolean more=page.getCurrent()*page.getPageSize()<page.getTotal();Map<String,Object> result=new LinkedHashMap<>();result.put("items",page.getList());result.put("nextCursor",more?String.valueOf(page.getCurrent()+1):null);result.put("hasMore",more);result.put("traceId",UUID.randomUUID().toString());return result;}

    private static Map<String,Object> dtoSchema(Class<?> type) {
        Map<String,Object> properties=new LinkedHashMap<>();
        List<String> required=new java.util.ArrayList<>();
        for(Class<?> current=type;current!=null&&current!=Object.class;current=current.getSuperclass()){
            for(Field field:current.getDeclaredFields()){
                if(java.lang.reflect.Modifier.isStatic(field.getModifiers())||field.isSynthetic())continue;
                Map<String,Object> schema=new LinkedHashMap<>(fieldSchema(field.getGenericType()));
                Size size=field.getAnnotation(Size.class);
                if(size!=null){
                    if(CharSequence.class.isAssignableFrom(field.getType())){schema.put("minLength",size.min());schema.put("maxLength",size.max());}
                    else if(field.getType().isArray()||java.util.Collection.class.isAssignableFrom(field.getType())){schema.put("minItems",size.min());schema.put("maxItems",size.max());}
                }
                Min min=field.getAnnotation(Min.class);if(min!=null)schema.put("minimum",min.value());
                Max max=field.getAnnotation(Max.class);if(max!=null)schema.put("maximum",max.value());
                properties.putIfAbsent(field.getName(),schema);
                if(field.isAnnotationPresent(NotBlank.class)||field.isAnnotationPresent(NotNull.class)||field.isAnnotationPresent(NotEmpty.class))required.add(field.getName());
            }
        }
        return objectSchema(properties,required.stream().distinct().toList());
    }

    private static Map<String,Object> fieldSchema(Type type){
        if(type instanceof ParameterizedType parameterized){
            Class<?> raw=(Class<?>)parameterized.getRawType();
            if(java.util.Collection.class.isAssignableFrom(raw))return Map.of("type","array","items",fieldSchema(parameterized.getActualTypeArguments()[0]));
            if(Map.class.isAssignableFrom(raw))return Map.of("type","object","additionalProperties",true);
        }
        if(type instanceof Class<?> clazz){
            if(clazz==String.class||clazz==Character.class||clazz==char.class)return stringSchema();
            if(clazz==Boolean.class||clazz==boolean.class)return Map.of("type","boolean");
            if(Number.class.isAssignableFrom(clazz)||clazz.isPrimitive())return Map.of("type",(clazz==Float.class||clazz==Double.class||clazz==float.class||clazz==double.class)?"number":"integer");
            if(clazz.isEnum())return Map.of("type","string","enum",java.util.Arrays.stream(clazz.getEnumConstants()).map(Object::toString).toList());
            if(clazz.isArray())return Map.of("type","array","items",fieldSchema(clazz.getComponentType()));
            return dtoSchema(clazz);
        }
        return Map.of();
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

    private static List<String> stringList(Map<String, Object> arguments, String key) {
        Object value = arguments.get(key);
        if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
            throw new MSException("Missing required argument: " + key);
        }
        return collection.stream().map(String::valueOf).toList();
    }

    private static int optionalInt(Map<String, Object> arguments, String key, int defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static boolean optionalBool(Map<String, Object> arguments, String key, boolean defaultValue) {
        Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        return BooleanUtils.toBoolean(String.valueOf(value));
    }
}
