package io.metersphere.agent.service;

import io.metersphere.agent.constants.AgentBlockedReason;
import io.metersphere.agent.constants.AgentTaskOrigin;
import io.metersphere.agent.dto.*;
import io.metersphere.agent.mapper.AgentExecutionMapper;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.uid.IDGenerator;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Service
public class AgentExecutionPreflightService {
    @Value("${metersphere.ai.execution.preflight-ttl-ms:600000}") private long ttlMs;
    @Resource private JdbcTemplate jdbcTemplate;
    @Resource private AgentProjectService projectService;
    @Resource private AgentExecutionMapper executionMapper;
    @Resource private AgentTestPlanQueryService testPlanService;
    @Resource private AgentEnvironmentProfileService environmentService;
    @Resource private AgentCredentialReferenceService credentialService;
    @Resource private AgentModelProfileService modelProfileService;
    @Resource private AgentFunctionalCaseSearchService caseSearchService;
    @Resource private AgentPromptTemplateService promptTemplateService;
    @Resource private AgentLoginProfileService loginProfileService;
    @Resource private TestAssetVersionService testAssetVersionService;
    @Resource private TestAssetCatalogService testAssetCatalogService;
    @Resource private AgentCaseExecutabilityService caseExecutabilityService;
    @Resource private List<AgentTestDataCleanupHandler> cleanupHandlers;

    @Transactional(rollbackFor = Exception.class)
    public AgentExecutionPreflightDTO preflight(AgentExecutionPreflightRequest request) {
        String projectId=projectService.resolveProjectId(request.getProjectId());
        String actorId=StringUtils.defaultIfBlank(SessionUtils.getUserId(), AgentExecutionActorContext.get());
        if (StringUtils.isBlank(actorId)) throw new MSException("AUTHENTICATION_REQUIRED");
        String origin=StringUtils.upperCase(request.getTaskOrigin());
        if (!Set.of(AgentTaskOrigin.PERSONAL_MCP,AgentTaskOrigin.PLATFORM_MANUAL,AgentTaskOrigin.PLATFORM_SCHEDULED).contains(origin))
            throw new MSException("TASK_ORIGIN_INVALID");
        String traceId=UUID.randomUUID().toString(); long now=System.currentTimeMillis();
        List<AgentPreflightCheckDTO> checks=new ArrayList<>();
        String blockedReason=null; String blockedDetail=null;
        List<String> original=resolveOriginalScope(request,projectId);
        List<String> added=unique(request.getExpandedCaseIds());
        try {
            validateScope(projectId,request,original,added);
            pass(checks,"SCOPE","Execution scope is valid",Map.of("original",original.size(),"added",added.size()),now);
        } catch (MSException ex) {
            blockedReason=AgentBlockedReason.BLOCKED_SCOPE.name(); blockedDetail=safe(ex);
            block(checks,"SCOPE",blockedDetail,now);
        }
        AgentEnvironmentProfileDTO environment=null;
        try {
            environment=environmentService.resolveForTask(request.getEnvironmentProfileId(),projectId);
            if ("PRODUCTION".equals(environment.getEnvironmentType())) throw new MSException("PRODUCTION_EXECUTION_FORBIDDEN");
            environmentService.assertTargetAllowed(environment,environment.getBaseUrl());
            pass(checks,"ENVIRONMENT","Environment profile is enabled and target is allowed",Map.of("profileId",environment.getId(),"version",environment.getVersion()),now);
        } catch (MSException ex) {
            if (blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_ENVIRONMENT.name();blockedDetail=safe(ex);} block(checks,"ENVIRONMENT",safe(ex),now);
        }
        String credentialVersion=null;
        AgentCredentialReferenceDTO credentialSnapshot=null;
        if(environment!=null&&StringUtils.isNotBlank(environment.getLoginProfileId())){
            try{loginProfileService.freeze(environment.getLoginProfileId(),projectId);pass(checks,"LOGIN_PROFILE","Automatic login profile is valid and frozen",Map.of("loginProfileId",environment.getLoginProfileId()),now);}catch(MSException ex){if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_CREDENTIAL.name();blockedDetail=safe(ex);}block(checks,"LOGIN_PROFILE",safe(ex),now);}
            if(StringUtils.isBlank(request.getCredentialReferenceId())){if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_CREDENTIAL.name();blockedDetail="CREDENTIAL_REQUIRED_FOR_AUTOMATIC_LOGIN";}block(checks,"CREDENTIAL","CREDENTIAL_REQUIRED_FOR_AUTOMATIC_LOGIN",now);}
        }
        if (StringUtils.isNotBlank(request.getCredentialReferenceId())) {
            try {
                AgentCredentialReferenceDTO credential=credentialService.assertUsable(request.getCredentialReferenceId(),projectId,
                        environment==null?null:environment.getEnvironmentId(),null);
                credentialSnapshot=credential;
                credentialVersion=credential.getSecretVersion();
                pass(checks,"CREDENTIAL","Credential reference is active",Map.of("referenceId",credential.getId(),"version",StringUtils.defaultString(credentialVersion)),now);
            } catch (MSException ex) {
                if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_CREDENTIAL.name();blockedDetail=safe(ex);} block(checks,"CREDENTIAL",safe(ex),now);
            }
        } else pass(checks,"CREDENTIAL","No credential is required",Map.of(),now);
        AgentModelProfileDTO model=null;
        AgentPromptTemplateVersionDTO promptVersion=null;
        if (!AgentTaskOrigin.PERSONAL_MCP.equals(origin)) {
            try {
                if (StringUtils.isBlank(request.getModelProfileId())) throw new MSException("MODEL_PROFILE_REQUIRED");
                model=modelProfileService.assertUsable(request.getModelProfileId(),projectId,List.of("STRUCTURED_OUTPUT"));
                pass(checks,"MODEL","MAP Gateway model profile is verified",Map.of("profileId",model.getId(),"version",model.getVersion()),now);
            } catch (MSException ex) {
                if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_MODEL.name();blockedDetail=safe(ex);} block(checks,"MODEL",safe(ex),now);
            }
            try {
                if(StringUtils.isBlank(request.getPromptTemplateId()))throw new MSException("PROMPT_TEMPLATE_REQUIRED");
                promptVersion=promptTemplateService.resolvePublished(request.getPromptTemplateId(),projectId);
                pass(checks,"PROMPT","Published prompt template is frozen",Map.of("templateId",promptVersion.getPromptTemplateId(),"versionId",promptVersion.getId(),"versionNo",promptVersion.getVersionNo()),now);
            } catch (MSException ex) {
                if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_MODEL.name();blockedDetail=safe(ex);} block(checks,"PROMPT",safe(ex),now);
            }
        } else pass(checks,"MODEL","Personal MCP performs planning outside the platform",Map.of(),now);
        try {
            checkQuota(request, model);
            pass(checks,"QUOTA","Model and execution budgets are valid",Map.of(),now);
        } catch (MSException ex) {
            if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_MODEL.name();blockedDetail=safe(ex);} block(checks,"QUOTA",safe(ex),now);
        }
        try {
            checkRiskPolicy(request, environment);
            pass(checks,"RISK_POLICY","Risk, production and scope-expansion policies are valid",Map.of("maxScopeExpansionRate",0.15d),now);
        } catch (MSException ex) {
            if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_POLICY.name();blockedDetail=safe(ex);} block(checks,"RISK_POLICY",safe(ex),now);
        }
        try {
            assertRunner(projectId,request,environment);
            pass(checks,"RUNNER","A matching Runner is online",Map.of(),now);
        } catch (MSException ex) {
            if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_RUNNER.name();blockedDetail=safe(ex);} block(checks,"RUNNER",safe(ex),now);
        }
        try {
            validateRecipients(origin,request.getResponsibleUserIds(),projectId);
            pass(checks,"RESPONSIBLE_USERS",AgentTaskOrigin.PLATFORM_SCHEDULED.equals(origin)?"Exactly three responsible users are valid":"Responsible-user check is not required",Map.of(),now);
        } catch (MSException ex) {
            if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_POLICY.name();blockedDetail=safe(ex);} block(checks,"RESPONSIBLE_USERS",safe(ex),now);
        }
        List<String> resolved=new ArrayList<>(original);resolved.addAll(added);
        List<AgentCaseExecutabilityDTO> executability=List.of();
        List<Map<String,Object>> executableAssets=List.of();
        if(environment!=null&&blockedReason==null){
            try{
                AgentCaseExecutabilityRequest executableRequest=new AgentCaseExecutabilityRequest();executableRequest.setProjectId(projectId);
                executableRequest.setEnvironmentProfileId(environment.getId());executableRequest.setCaseIds(resolved);
                executability=caseExecutabilityService.batchCheck(executableRequest);
                List<AgentCaseExecutabilityDTO> notReady=executability.stream().filter(v->!"READY".equals(v.getAutomationReadiness())).toList();
                if(!notReady.isEmpty())throw new MSException("CASE_AI_EXECUTABILITY_NOT_READY: "+notReady.stream().map(AgentCaseExecutabilityDTO::getCaseId).toList());
                if(credentialSnapshot!=null){
                    String actualRole=credentialSnapshot.getBusinessRole();
                    if(executability.stream().map(AgentCaseExecutabilityDTO::getCredentialRole).filter(StringUtils::isNotBlank).anyMatch(role->!StringUtils.equals(role,actualRole)))
                        throw new MSException("CASE_CREDENTIAL_ROLE_MISMATCH");
                }
                executableAssets=freezeExecutableAssets(projectId,executability);
                pass(checks,"CASE_EXECUTABILITY","All cases are READY for the selected environment and credential role",Map.of("count",executability.size()),now);
            }catch(MSException ex){blockedReason=AgentBlockedReason.BLOCKED_SCOPE.name();blockedDetail=safe(ex);block(checks,"CASE_EXECUTABILITY",blockedDetail,now);}
        }
        List<Map<String,Object>> caseAssets=List.of();
        List<Map<String,Object>> documentAssets=List.of();
        List<TestAssetContextDTO> additionalAssets=List.of();
        if(blockedReason==null){
            try{
                caseAssets=freezeCaseAssets(projectId,resolved,request.getTestPlanId(),actorId);
                documentAssets=freezeRelatedDocuments(projectId,caseAssets);
                pass(checks,"PUBLISHED_ASSETS","Case, document and executable assets are frozen from published immutable versions",Map.of("caseCount",caseAssets.size(),"documentCount",documentAssets.size()),now);
            }catch(MSException ex){
                blockedReason=AgentBlockedReason.BLOCKED_SCOPE.name();blockedDetail=safe(ex);block(checks,"PUBLISHED_ASSETS",blockedDetail,now);
            }
        }
        if(blockedReason==null){
            try{
                List<TestAssetRefDTO> refs=new ArrayList<>(request.getAssetRefs()==null?List.of():request.getAssetRefs());
                String environmentAssetId=environment==null?null:environment.getEnvironmentId();
                if(environmentAssetId!=null&&refs.stream().noneMatch(ref->"ENVIRONMENT".equals(StringUtils.upperCase(ref.getAssetType()))&&environmentAssetId.equals(ref.getAssetId()))){TestAssetRefDTO environmentRef=new TestAssetRefDTO();environmentRef.setAssetType("ENVIRONMENT");environmentRef.setAssetId(environmentAssetId);refs.add(environmentRef);}
                request.setAssetRefs(refs);
                additionalAssets=testAssetCatalogService.resolveContext(projectId,refs);
                pass(checks,"ADDITIONAL_ASSETS","Explicit asset references are published, authorized and frozen",Map.of("count",additionalAssets.size()),now);
            }catch(MSException ex){blockedReason=AgentBlockedReason.BLOCKED_SCOPE.name();blockedDetail=safe(ex);block(checks,"ADDITIONAL_ASSETS",blockedDetail,now);}
        }
        try {
            checkTestData(projectId,executableAssets);
            pass(checks,"TEST_DATA","Referenced datasets are published and bound to the frozen execution scope",Map.of(),now);
        } catch (MSException ex) {
            if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_DATA.name();blockedDetail=safe(ex);} block(checks,"TEST_DATA",safe(ex),now);
        }
        try {
            checkCleanupPolicy(request, executableAssets);
            pass(checks,"CLEANUP_POLICY","An idempotent cleanup handler is available for leased test data",Map.of(),now);
        } catch (MSException ex) {
            if(blockedReason==null){blockedReason=AgentBlockedReason.BLOCKED_POLICY.name();blockedDetail=safe(ex);} block(checks,"CLEANUP_POLICY",safe(ex),now);
        }
        String scopeHash=sha(JSON.toJSONString(resolved));
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("schemaVersion","v1");snapshot.put("projectId",projectId);snapshot.put("taskOrigin",origin);
        snapshot.put("originalCaseIds",original);snapshot.put("addedCaseIds",added);snapshot.put("expansionReasons",safeReasons(request.getExpansionReasons(),added));
        snapshot.put("caseAssets",caseAssets);
        snapshot.put("documentAssets",documentAssets);
        snapshot.put("additionalAssets",additionalAssets);
        snapshot.put("caseExecutability",executability);
        snapshot.put("executableAssets",executableAssets);
        if(environment!=null)snapshot.put("environment",JSON.parseObject(environmentService.freezeSnapshot(environment),Map.class));
        if(credentialSnapshot!=null)snapshot.put("credential",Map.of(
                "id",credentialSnapshot.getId(),"businessRole",StringUtils.defaultIfBlank(credentialSnapshot.getBusinessRole(),"DEFAULT"),
                "credentialType",credentialSnapshot.getCredentialType(),"secretVersion",StringUtils.defaultString(credentialSnapshot.getSecretVersion())));
        if(model!=null)snapshot.put("model",JSON.parseObject(modelProfileService.freeze(model.getId()),Map.class));
        if(promptVersion!=null)snapshot.put("promptTemplate",JSON.parseObject(promptTemplateService.freeze(promptVersion),Map.class));
        snapshot.put("requiredCapabilities",unique(request.getRequiredCapabilities()));snapshot.put("policy",request.getPolicy()==null?Map.of():request.getPolicy());
        String snapshotJson=JSON.toJSONString(snapshot);String snapshotHash=sha(snapshotJson);
        String requestJson=JSON.toJSONString(request);String requestHash=sha(requestJson);
        String id=IDGenerator.nextStr();String status=blockedReason==null?"PASSED":"BLOCKED";long expiresAt=now+ttlMs;
        BigDecimal rate=original.isEmpty()?BigDecimal.ZERO:BigDecimal.valueOf(added.size()).divide(BigDecimal.valueOf(original.size()),4,RoundingMode.HALF_UP);
        jdbcTemplate.update("""
          INSERT INTO ai_execution_preflight
          (id,task_id,project_id,actor_id,task_origin,request_hash,request_json,trace_id,status,checks_json,resolved_scope_json,
           snapshot_json,original_scope_count,expanded_scope_count,scope_expansion_rate,scope_hash,asset_snapshot_hash,
           environment_profile_version,credential_secret_version,model_profile_version,runner_capability_hash,
           blocked_reason,blocked_detail,started_at,finished_at,expires_at,create_time)
          VALUES (?,NULL,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
          """,id,projectId,actorId,origin,requestHash,requestJson,traceId,status,JSON.toJSONString(checks),JSON.toJSONString(resolved),
                snapshotJson,original.size(),added.size(),rate,scopeHash,snapshotHash,environment==null?null:environment.getVersion(),credentialVersion,
                model==null?null:model.getVersion(),sha(JSON.toJSONString(unique(request.getRequiredCapabilities()))),blockedReason,blockedDetail,now,now,expiresAt,now);
        return get(id);
    }

    public AgentExecutionPreflightDTO get(String id) {
        List<AgentExecutionPreflightDTO> rows=jdbcTemplate.query("SELECT * FROM ai_execution_preflight WHERE id=?",(rs,row)->{
            AgentExecutionPreflightDTO d=new AgentExecutionPreflightDTO();d.setId(rs.getString("id"));d.setProjectId(rs.getString("project_id"));d.setTaskOrigin(rs.getString("task_origin"));d.setStatus(rs.getString("status"));d.setChecks(JSON.parseArray(rs.getString("checks_json"),AgentPreflightCheckDTO.class));List<String> resolved=JSON.parseArray(rs.getString("resolved_scope_json"),String.class);d.setResolvedCaseIds(resolved);Map<String,Object> snap=JSON.parseObject(rs.getString("snapshot_json"),Map.class);d.setOriginalCaseIds(stringList(snap.get("originalCaseIds")));d.setAddedCaseIds(stringList(snap.get("addedCaseIds")));d.setReasonByCase(JSON.parseObject(JSON.toJSONString(snap.get("expansionReasons")),Map.class));Map<String,Object> prompt=mapValue(snap.get("promptTemplate"));d.setPromptTemplateVersionId(prompt==null?null:String.valueOf(prompt.get("id")));d.setOriginalScopeCount(rs.getInt("original_scope_count"));d.setExpandedScopeCount(rs.getInt("expanded_scope_count"));d.setScopeExpansionRate(rs.getBigDecimal("scope_expansion_rate"));d.setSnapshotHash(rs.getString("asset_snapshot_hash"));d.setBlockedReason(rs.getString("blocked_reason"));d.setBlockedDetail(rs.getString("blocked_detail"));d.setTraceId(rs.getString("trace_id"));d.setExpiresAt(rs.getLong("expires_at"));return d;
        },id);
        if(rows.isEmpty())throw new MSException("PREFLIGHT_NOT_FOUND");
        projectService.resolveProjectId(rows.getFirst().getProjectId());return rows.getFirst();
    }

    public AgentExecutionPreflightDTO validateForCreate(String id, String projectId, String actorId, String taskOrigin,
                                                         AgentExecutionCreateRequest create) {
        AgentExecutionPreflightDTO dto = get(id);
        Map<String,Object> row = jdbcTemplate.queryForMap("SELECT actor_id,task_origin,status,expires_at,task_id,request_json FROM ai_execution_preflight WHERE id=?", id);
        if (!projectId.equals(dto.getProjectId()) || !actorId.equals(row.get("actor_id")) || !taskOrigin.equals(row.get("task_origin"))
                || !"PASSED".equals(row.get("status")) || row.get("task_id") != null || ((Number)row.get("expires_at")).longValue() <= System.currentTimeMillis()) {
            throw new MSException("PREFLIGHT_INVALID_EXPIRED_OR_ALREADY_USED");
        }
        AgentExecutionPreflightRequest request = JSON.parseObject((String) row.get("request_json"), AgentExecutionPreflightRequest.class);
        if (!StringUtils.equals(request.getEnvironmentProfileId(), create.getEnvironmentProfileId())
                || !StringUtils.equals(request.getCredentialReferenceId(), create.getCredentialReferenceId())
                || !StringUtils.equals(request.getModelProfileId(), create.getModelProfileId())
                || !StringUtils.equals(request.getTestPlanId(), create.getTestPlanId())
                || !StringUtils.equals(StringUtils.upperCase(StringUtils.trimToNull(request.getBrowserType())),StringUtils.upperCase(StringUtils.trimToNull(create.getBrowserType())))
                || !StringUtils.equals(dto.getPromptTemplateVersionId(), create.getPromptTemplateVersionId())
                || !unique(request.getCaseIds()).equals(unique(create.getCaseIds()))) {
            throw new MSException("PREFLIGHT_REQUEST_MISMATCH");
        }
        // The preflight request is the authoritative contract.  Never retain mutable
        // policy or capability values supplied again by the create caller.
        create.setRequiredCapabilities(unique(request.getRequiredCapabilities()));
        create.setPolicySnapshot(JSON.toJSONString(request.getPolicy()==null?Map.of():request.getPolicy()));
        Object approval=(request.getPolicy()==null?null:request.getPolicy().get("approvalPolicy"));
        create.setApprovalPolicy(approval==null?null:JSON.toJSONString(approval));
        create.setCaseIds(dto.getResolvedCaseIds());
        create.setBrowserType(StringUtils.upperCase(StringUtils.trimToNull(request.getBrowserType())));
        create.setAssetRefs(frozenAssetRefs(id));
        return dto;
    }

    private List<TestAssetRefDTO> frozenAssetRefs(String preflightId){
        Map<String,Object> row=jdbcTemplate.queryForMap("SELECT snapshot_json FROM ai_execution_preflight WHERE id=?",preflightId);
        Map<String,Object> snapshot=JSON.parseObject((String)row.get("snapshot_json"),Map.class);
        @SuppressWarnings("unchecked") List<Map<String,Object>> assets=(List<Map<String,Object>>)snapshot.get("additionalAssets");
        if(assets==null)return List.of();
        return assets.stream().map(asset->{TestAssetRefDTO ref=new TestAssetRefDTO();ref.setAssetType(String.valueOf(asset.get("assetType")));ref.setAssetId(String.valueOf(asset.get("assetId")));ref.setVersionId(String.valueOf(asset.get("versionId")));return ref;}).toList();
    }

    public TestAssetVersionDTO assertFrozenCaseVersion(String preflightId,String caseRowId,String stableAssetId,String contentSnapshot){
        Map<String,Object> row=jdbcTemplate.queryForMap("SELECT snapshot_json FROM ai_execution_preflight WHERE id=?",preflightId);
        Map<String,Object> snapshot=JSON.parseObject((String)row.get("snapshot_json"),Map.class);
        @SuppressWarnings("unchecked") List<Map<String,Object>> assets=(List<Map<String,Object>>)snapshot.get("caseAssets");
        if(assets==null)throw new MSException("PREFLIGHT_CASE_ASSET_SNAPSHOT_MISSING");
        Map<String,Object> frozen=assets.stream().filter(v->caseRowId.equals(String.valueOf(v.get("caseRowId")))).findFirst().orElseThrow(()->new MSException("PREFLIGHT_CASE_ASSET_NOT_FROZEN"));
        if(!stableAssetId.equals(String.valueOf(frozen.get("assetId")))||!sha(contentSnapshot).equals(String.valueOf(frozen.get("contentHash"))))throw new MSException("PREFLIGHT_CASE_ASSET_CHANGED");
        return testAssetVersionService.getFrozen(String.valueOf(frozen.get("versionId")),snapshot.get("projectId").toString(),"CASE",stableAssetId);
    }

    public String frozenSnapshotSection(String preflightId, String section) {
        Map<String,Object> row=jdbcTemplate.queryForMap("SELECT snapshot_json FROM ai_execution_preflight WHERE id=?",preflightId);
        Map<String,Object> snapshot=JSON.parseObject((String)row.get("snapshot_json"),Map.class);
        Object value=snapshot.get(section);
        if(value==null)throw new MSException("PREFLIGHT_SNAPSHOT_SECTION_MISSING: "+section);
        return JSON.toJSONString(value);
    }

    public List<TestAssetContextDTO> frozenExecutableAssetContexts(String preflightId) {
        List<Map> refs=JSON.parseArray(frozenSnapshotSection(preflightId,"executableAssets"),Map.class);
        List<TestAssetContextDTO> result=new ArrayList<>();
        for(Map ref:refs){
            String type=String.valueOf(ref.get("assetType"));String assetId=String.valueOf(ref.get("assetId"));
            TestAssetVersionDTO version=testAssetVersionService.getFrozen(String.valueOf(ref.get("versionId")),get(preflightId).getProjectId(),type,assetId);
            TestAssetContextDTO context=new TestAssetContextDTO();context.setAssetType(type);context.setAssetId(assetId);context.setVersionId(version.getId());
            context.setVersionNo(version.getVersionNo());context.setContentHash(version.getContentHash());context.setContentSnapshot(version.getContentSnapshot());
            result.add(context);
        }
        return result;
    }

    @Transactional(rollbackFor=Exception.class)
    public AgentExecutionPreflightDTO consume(String id,String projectId,String actorId,String taskOrigin,String taskId){
        AgentExecutionPreflightDTO dto=get(id);long now=System.currentTimeMillis();
        Integer changed=jdbcTemplate.update("UPDATE ai_execution_preflight SET task_id=? WHERE id=? AND task_id IS NULL AND project_id=? AND actor_id=? AND task_origin=? AND status='PASSED' AND expires_at>?",taskId,id,projectId,actorId,taskOrigin,now);
        if(changed!=1)throw new MSException("PREFLIGHT_INVALID_EXPIRED_OR_ALREADY_USED");return dto;
    }

    private List<String> resolveOriginalScope(AgentExecutionPreflightRequest r,String projectId){
        List<String> ids=unique(r.getCaseIds());
        if(!ids.isEmpty())return ids;
        if(StringUtils.isNotBlank(r.getTestPlanId())){
            List<String> result=new ArrayList<>();int page=1;
            while(true){AgentCaseSearchResponse response=testPlanService.cases(projectId,r.getTestPlanId(),page,100,false);response.getCases().forEach(c->result.add(c.getCaseId()));if((long)page*100>=response.getTotal())break;page++;}
            return result.stream().distinct().toList();
        }
        if(r.getCaseFilter()!=null&&!r.getCaseFilter().isEmpty()){
            AgentCaseSearchRequest search=JSON.parseObject(JSON.toJSONString(r.getCaseFilter()),AgentCaseSearchRequest.class);search.setProjectId(projectId);search.setIncludeSteps(false);search.setCurrent(1);search.setPageSize(100);AgentCaseSearchResponse result=caseSearchService.search(search);if(result.getTotal()>100)throw new MSException("EXECUTION_SCOPE_TOO_LARGE");return result.getCases().stream().map(AgentCaseDTO::getCaseId).distinct().toList();
        }
        throw new MSException("EXECUTION_SCOPE_EMPTY");
    }
    private void validateScope(String projectId,AgentExecutionPreflightRequest r,List<String> original,List<String> added){
        if(original.isEmpty())throw new MSException("EXECUTION_SCOPE_EMPTY");
        Set<String> overlap=new HashSet<>(original);overlap.retainAll(added);if(!overlap.isEmpty())throw new MSException("SCOPE_EXPANSION_DUPLICATE");
        if(!added.isEmpty()&&((double)added.size()/original.size())>0.15d)throw new MSException("SCOPE_EXPANSION_LIMIT_EXCEEDED");
        if(original.isEmpty()&&!added.isEmpty())throw new MSException("SCOPE_EXPANSION_WITHOUT_BASE");
        List<String> all=new ArrayList<>(original);all.addAll(added);if(all.size()>100)throw new MSException("EXECUTION_SCOPE_TOO_LARGE");
        String placeholders=String.join(",",Collections.nCopies(all.size(),"?"));List<Object> args=new ArrayList<>();args.add(projectId);args.addAll(all);
        Integer count=jdbcTemplate.queryForObject("SELECT COUNT(1) FROM functional_case WHERE project_id=? AND deleted=0 AND id IN ("+placeholders+")",Integer.class,args.toArray());
        if(count==null||count!=all.size())throw new MSException("SCOPE_CASE_PROJECT_MISMATCH");
        if(!added.isEmpty()&&(r.getExpansionReasons()==null||added.stream().anyMatch(id->StringUtils.isBlank(r.getExpansionReasons().get(id)))))throw new MSException("SCOPE_EXPANSION_REASON_REQUIRED");
        if(!added.isEmpty()){
            String addedPlaceholders=String.join(",",Collections.nCopies(added.size(),"?"));List<Object> lowRiskArgs=new ArrayList<>();lowRiskArgs.add(projectId);lowRiskArgs.addAll(added);
            Integer lowRisk=jdbcTemplate.queryForObject("SELECT COUNT(1) FROM functional_case WHERE project_id=? AND deleted=0 AND review_status='PASS' AND id IN ("+addedPlaceholders+") AND UPPER(COALESCE(CAST(tags AS CHAR),'')) NOT LIKE '%HIGH_RISK%' AND UPPER(COALESCE(CAST(tags AS CHAR),'')) NOT LIKE '%PRODUCTION%'",Integer.class,lowRiskArgs.toArray());
            if(lowRisk==null||lowRisk!=added.size())throw new MSException("SCOPE_EXPANSION_CASE_NOT_LOW_RISK_OR_REVIEWED");
        }
    }
    private List<Map<String,Object>> freezeCaseAssets(String projectId,List<String> caseIds,String testPlanId,String actorId){
        List<Map<String,Object>> result=new ArrayList<>();
        for(String caseId:caseIds){
            Map<String,Object> identity=jdbcTemplate.queryForMap("SELECT id,ref_id,update_time FROM functional_case WHERE id=? AND project_id=? AND deleted=0",caseId,projectId);
            String stableId=StringUtils.defaultIfBlank((String)identity.get("ref_id"),(String)identity.get("id"));
            TestAssetVersionDTO version=testAssetVersionService.latestPublished(projectId,"CASE",stableId);
            result.add(Map.of("caseRowId",caseId,"assetId",stableId,"versionId",version.getId(),"versionNo",version.getVersionNo(),"contentHash",version.getContentHash()));
        }
        return result;
    }

    public TestAssetVersionDTO assertFrozenExecutableAsset(String preflightId,String assetType,String assetId){
        Map<String,Object> row=jdbcTemplate.queryForMap("SELECT project_id,snapshot_json,status,expires_at FROM ai_execution_preflight WHERE id=?",preflightId);
        if(!"PASSED".equals(row.get("status")))throw new MSException("PREFLIGHT_NOT_PASSED");
        Map<String,Object> snapshot=JSON.parseObject((String)row.get("snapshot_json"),Map.class);
        @SuppressWarnings("unchecked") List<Map<String,Object>> assets=(List<Map<String,Object>>)snapshot.get("executableAssets");
        Map<String,Object> frozen=(assets==null?List.<Map<String,Object>>of():assets).stream()
                .filter(v->assetType.equals(v.get("assetType"))&&assetId.equals(String.valueOf(v.get("assetId"))))
                .findFirst().orElseThrow(()->new MSException("TEST_DATASET_NOT_IN_FROZEN_SCOPE"));
        return testAssetVersionService.getFrozen(String.valueOf(frozen.get("versionId")),String.valueOf(row.get("project_id")),assetType,assetId);
    }
    private List<Map<String,Object>> freezeRelatedDocuments(String projectId,List<Map<String,Object>> caseAssets){
        List<String> stableCaseIds=caseAssets.stream().map(v->String.valueOf(v.get("assetId"))).toList();
        return testAssetCatalogService.documentContextForCases(projectId,stableCaseIds).stream().map(document->{
            TestAssetVersionDTO version=testAssetVersionService.getPublished(document.getVersionId(),projectId,"DOCUMENT",document.getDocumentId());
            return Map.<String,Object>of("assetType","DOCUMENT","assetId",document.getDocumentId(),"versionId",version.getId(),"versionNo",version.getVersionNo(),"contentHash",version.getContentHash());
        }).distinct().toList();
    }
    private List<Map<String,Object>> freezeExecutableAssets(String projectId,List<AgentCaseExecutabilityDTO> configs){
        Set<String> refs=new LinkedHashSet<>();
        for(AgentCaseExecutabilityDTO config:configs){
            config.getPageObjectIds().forEach(id->refs.add("PAGE_OBJECT:"+id));
            config.getDatasetIds().forEach(id->refs.add("DATASET:"+id));
            if(StringUtils.isNotBlank(config.getBusinessFlowId()))refs.add("BUSINESS_FLOW:"+config.getBusinessFlowId());
        }
        List<Map<String,Object>> result=new ArrayList<>();
        for(String ref:refs){String[] parts=ref.split(":",2);TestAssetVersionDTO version=testAssetVersionService.latestPublished(projectId,parts[0],parts[1]);
            result.add(Map.of("assetType",parts[0],"assetId",parts[1],"versionId",version.getId(),"versionNo",version.getVersionNo(),"contentHash",version.getContentHash()));}
        return result;
    }
    private void assertRunner(String projectId,AgentExecutionPreflightRequest r,AgentEnvironmentProfileDTO env){
        if(AgentTaskOrigin.PERSONAL_MCP.equals(StringUtils.upperCase(r.getTaskOrigin())))return;
        String org=jdbcTemplate.queryForObject("SELECT organization_id FROM project WHERE id=?",String.class,projectId);
        List<Map<String,Object>> runners=jdbcTemplate.queryForList("SELECT browser_capabilities,browser_types,last_heartbeat_time FROM ai_runner WHERE organization_id=? AND status='ONLINE' AND runner_type=? AND active_count<max_concurrency AND last_heartbeat_time>=? AND (network_zone IS NULL OR network_zone=? OR ? IS NULL)",org,StringUtils.upperCase(r.getRunnerType()),System.currentTimeMillis()-90_000L,env==null?null:env.getNetworkZone(),env==null?null:env.getNetworkZone());
        List<String> required=unique(r.getRequiredCapabilities());
        boolean matched=runners.stream().anyMatch(row->{Set<String> available=new HashSet<>();available.addAll(jsonStrings(row.get("browser_capabilities")));available.addAll(jsonStrings(row.get("browser_types")));return available.containsAll(required);});
        if(!matched)throw new MSException("RUNNER_NOT_AVAILABLE_OR_CAPABILITY_MISMATCH");
    }
    private void validateRecipients(String origin,List<String> users,String projectId){
        if(!AgentTaskOrigin.PLATFORM_SCHEDULED.equals(origin))return;List<String> ids=unique(users);if(ids.size()!=3)throw new MSException("RESPONSIBLE_USERS_MUST_BE_EXACTLY_THREE");String ph=String.join(",",Collections.nCopies(3,"?"));List<Object>a=new ArrayList<>(ids);a.add(projectId);Integer count=jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT u.id) FROM user u JOIN user_role_relation urr ON urr.user_id=u.id WHERE u.enable=1 AND u.deleted=0 AND u.id IN ("+ph+") AND urr.source_id=?",Integer.class,a.toArray());if(count==null||count!=3)throw new MSException("RESPONSIBLE_USER_INVALID_OR_UNAUTHORIZED");
    }
    private void checkQuota(AgentExecutionPreflightRequest request,AgentModelProfileDTO model){
        Map<String,Object> policy=request.getPolicy()==null?Map.of():request.getPolicy();
        long maxCalls=positiveLong(policy.get("maxModelInvocations"),100);
        if(maxCalls<1||maxCalls>100)throw new MSException("MODEL_INVOCATION_LIMIT_INVALID");
        long maxMinutes=positiveLong(policy.get("maxExecutionMinutes"),120);
        if(maxMinutes<1||maxMinutes>1440)throw new MSException("EXECUTION_TIME_BUDGET_INVALID");
        if(!AgentTaskOrigin.PERSONAL_MCP.equals(StringUtils.upperCase(request.getTaskOrigin()))&&model!=null&&model.getMaxCostAmount()!=null&&model.getMaxCostAmount().signum()<=0)
            throw new MSException("MODEL_COST_BUDGET_INVALID");
    }
    private void checkRiskPolicy(AgentExecutionPreflightRequest request,AgentEnvironmentProfileDTO environment){
        if(environment!=null&&"PRODUCTION".equals(environment.getEnvironmentType()))throw new MSException("PRODUCTION_EXECUTION_FORBIDDEN");
        Map<String,Object> policy=request.getPolicy()==null?Map.of():request.getPolicy();
        double configured=decimal(policy.get("scopeExpansionLimit"),0.15d);
        if(configured<0||configured>0.15d)throw new MSException("SCOPE_EXPANSION_POLICY_INVALID");
        String action=StringUtils.upperCase(String.valueOf(policy.getOrDefault("riskActionPolicy","SKIP_AND_REVIEW")));
        if(!Set.of("SKIP_AND_REVIEW","BLOCK").contains(action))throw new MSException("RISK_ACTION_POLICY_INVALID");
    }
    private void checkTestData(String projectId,List<Map<String,Object>> executableAssets){
        for(Map<String,Object> asset:executableAssets){
            if("DATASET".equals(asset.get("assetType")))testAssetVersionService.getPublished(String.valueOf(asset.get("versionId")),
                    projectId,"DATASET",String.valueOf(asset.get("assetId")));
        }
    }
    private void checkCleanupPolicy(AgentExecutionPreflightRequest request,List<Map<String,Object>> executableAssets){
        boolean datasets=executableAssets.stream().anyMatch(v->"DATASET".equals(v.get("assetType")));
        if(!datasets)return;
        Object configured=request.getPolicy()==null?null:request.getPolicy().get("cleanupRequired");
        if(Boolean.FALSE.equals(configured))throw new MSException("TEST_DATA_CLEANUP_REQUIRED");
        if(cleanupHandlers==null||cleanupHandlers.stream().noneMatch(v->v.supports("DATASET")))throw new MSException("TEST_DATA_CLEANUP_HANDLER_NOT_CONFIGURED");
    }
    private long positiveLong(Object value,long fallback){if(value==null)return fallback;try{return Long.parseLong(String.valueOf(value));}catch(NumberFormatException ex){throw new MSException("POLICY_NUMBER_INVALID");}}
    private double decimal(Object value,double fallback){if(value==null)return fallback;try{return Double.parseDouble(String.valueOf(value));}catch(NumberFormatException ex){throw new MSException("POLICY_NUMBER_INVALID");}}
    private void pass(List<AgentPreflightCheckDTO> c,String code,String msg,Map<String,Object>d,long now){c.add(new AgentPreflightCheckDTO(code,"PASSED",msg,d,now));}
    private void block(List<AgentPreflightCheckDTO> c,String code,String msg,long now){c.add(new AgentPreflightCheckDTO(code,"BLOCKED",msg,Map.of(),now));}
    private String safe(Throwable e){String m=StringUtils.defaultIfBlank(e.getMessage(),"PREFLIGHT_CHECK_FAILED");return m.matches("[A-Z0-9_]+")?m:"PREFLIGHT_CHECK_FAILED";}
    private List<String> unique(List<String> v){return v==null?List.of():v.stream().filter(StringUtils::isNotBlank).map(String::trim).distinct().sorted().toList();}
    private Map<String,String> safeReasons(Map<String,String> reasons,List<String> added){Map<String,String> out=new LinkedHashMap<>();for(String id:added)out.put(id,StringUtils.abbreviate(StringUtils.defaultString(reasons==null?null:reasons.get(id)),500));return out;}
    @SuppressWarnings("unchecked") private List<String> stringList(Object value){if(value==null)return List.of();return ((List<Object>)value).stream().map(String::valueOf).toList();}
    @SuppressWarnings("unchecked") private Map<String,Object> mapValue(Object value){return value instanceof Map<?,?>?(Map<String,Object>)value:null;}
    private List<String> jsonStrings(Object value){if(value==null||StringUtils.isBlank(String.valueOf(value)))return List.of();try{return JSON.parseArray(String.valueOf(value),String.class).stream().map(StringUtils::upperCase).toList();}catch(Exception e){return Arrays.stream(String.valueOf(value).split(",")).map(String::trim).map(StringUtils::upperCase).filter(StringUtils::isNotBlank).toList();}}
    private String sha(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
