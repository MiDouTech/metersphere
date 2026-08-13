package io.metersphere.functional.service;

import io.metersphere.functional.constants.FunctionalCaseAiDraftStatus;
import io.metersphere.functional.constants.FunctionalCaseAiGenerationStatus;
import io.metersphere.functional.constants.FunctionalCaseTypeConstants;
import io.metersphere.functional.domain.AiSourceDocument;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseAiDraft;
import io.metersphere.functional.domain.FunctionalCaseAiGeneration;
import io.metersphere.functional.domain.FunctionalCaseExample;
import io.metersphere.functional.dto.CaseCustomFieldDTO;
import io.metersphere.functional.dto.CaseGenerationCaseDTO;
import io.metersphere.functional.dto.CaseGenerationResult;
import io.metersphere.functional.dto.FunctionalCaseAiDTO;
import io.metersphere.functional.dto.FunctionalCaseAiDraftDTO;
import io.metersphere.functional.dto.FunctionalCaseStepDTO;
import io.metersphere.functional.event.TestAssetCasePublishedEvent;
import io.metersphere.functional.mapper.FunctionalCaseAiDraftMapper;
import io.metersphere.functional.mapper.FunctionalCaseAiGenerationMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.functional.mapper.AiSourceDocumentMapper;
import io.metersphere.functional.request.FunctionalCaseAddRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftBatchDeleteRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftBatchSaveRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftPageRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftRegenerateRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftReviewRequest;
import io.metersphere.functional.request.FunctionalCaseAiDraftUpsertRequest;
import io.metersphere.functional.request.FunctionalCaseAiGenerateRequest;
import io.metersphere.functional.request.FunctionalCaseAiGenerationCancelRequest;
import io.metersphere.functional.response.FunctionalCaseAiBatchSaveResponse;
import io.metersphere.functional.response.FunctionalCaseAiDraftPageResponse;
import io.metersphere.functional.response.FunctionalCaseAiGenerateResponse;
import io.metersphere.functional.utils.CaseGenerationJsonSchemaValidator;
import io.metersphere.functional.utils.MdUtil;
import io.metersphere.project.domain.Project;
import io.metersphere.project.mapper.ProjectMapper;
import io.metersphere.project.service.ProjectTemplateService;
import io.metersphere.sdk.constants.HttpMethodConstants;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.sdk.TemplateDTO;
import io.metersphere.system.log.constants.OperationLogModule;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.log.dto.LogDTO;
import io.metersphere.system.log.service.OperationLogService;
import io.metersphere.system.service.AiChatBaseService;
import io.metersphere.system.service.ai.AiGovernanceService;
import io.metersphere.system.service.ai.provider.AiProviderAdapter;
import io.metersphere.system.dto.request.ai.AiProviderChatRequest;
import io.metersphere.system.dto.request.ai.AiProviderInvocationResult;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class FunctionalCaseAiDraftService {
    private static final int DEFAULT_MAX_CASES = 50;
    private static final int MAX_CASES = 100;

    @Resource
    private FunctionalCaseAiGenerationMapper generationMapper;
    @Resource
    private FunctionalCaseAiDraftMapper draftMapper;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private AiSourceDocumentMapper aiSourceDocumentMapper;
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private ProjectTemplateService projectTemplateService;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private AiChatBaseService aiChatBaseService;
    @Resource
    private OperationLogService operationLogService;
    @Resource
    private AiGovernanceService aiGovernanceService;
    @Resource
    private AiProviderAdapter aiProviderAdapter;
    @Resource
    private PlatformTransactionManager transactionManager;
    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FunctionalCaseAiGenerateResponse generate(FunctionalCaseAiGenerateRequest request, String userId) {
        aiGovernanceService.assertModelAllowed(request.getProjectId(), request.getChatModelId());
        int maxCases = normalizeMaxCases(request.getMaxCases());
        long startTime = System.currentTimeMillis();
        FunctionalCaseAiGeneration generation = createGeneration(request, userId, startTime);
        aiGovernanceService.admitGeneration(request.getProjectId(), () -> generationMapper.insert(generation));
        try {
            AiProviderInvocationResult providerResult = callAiForStructuredCases(request, userId, maxCases);
            String rawContent = providerResult.getContent();
            if (isCanceled(generation.getId())) {
                audit("GENERATE_CANCELED", request.getProjectId(), userId, "generationId=" + generation.getId());
                FunctionalCaseAiGenerateResponse canceled = new FunctionalCaseAiGenerateResponse();
                canceled.setGenerationId(generation.getId());
                canceled.setCreatedCount(0);
                canceled.setDrafts(Collections.emptyList());
                canceled.setWarnings(List.of("生成任务已取消"));
                return canceled;
            }
            CaseGenerationResult result = parseGenerationResult(rawContent);
            List<CaseGenerationCaseDTO> cases = sanitizeAndLimit(result, maxCases);
            if (result.getWarnings() == null) {
                result.setWarnings(new ArrayList<>());
            }
            if (CollectionUtils.isEmpty(cases)) {
                throw new MSException("AI 未返回有效用例，未创建草稿");
            }

            List<FunctionalCaseAiDraft> draftEntities = new ArrayList<>();
            List<FunctionalCaseAiDraftDTO> createdDrafts = new ArrayList<>();
            for (CaseGenerationCaseDTO item : cases) {
                FunctionalCaseAiDraft draft = buildDraft(request, item, generation.getId(), userId);
                validateDraft(draft);
                draftEntities.add(draft);
                createdDrafts.add(toDTO(draft));
            }

            generation.setStatus(FunctionalCaseAiGenerationStatus.GENERATED.name());
            generation.setDurationMs(System.currentTimeMillis() - startTime);
            generation.setTokenUsage(providerResult.getTotalTokens());
            generation.setUpdateTime(System.currentTimeMillis());
            if (CollectionUtils.isNotEmpty(result.getWarnings())) {
                generation.setErrorMessage(String.join("; ", result.getWarnings()));
            }
            Boolean persisted = new TransactionTemplate(transactionManager).execute(status -> {
                if (generationMapper.updateTerminalIfActive(generation) == 0) {
                    return false;
                }
                draftEntities.forEach(draftMapper::insert);
                return true;
            });
            if (!Boolean.TRUE.equals(persisted)) {
                return canceledResponse(generation.getId());
            }

            FunctionalCaseAiGenerateResponse response = new FunctionalCaseAiGenerateResponse();
            response.setGenerationId(generation.getId());
            response.setCreatedCount(createdDrafts.size());
            response.setDrafts(createdDrafts);
            response.setWarnings(result.getWarnings());
            audit("GENERATE", request.getProjectId(), userId, "generationId=" + generation.getId() + ",drafts=" + createdDrafts.size());
            return response;
        } catch (Exception ex) {
            if (isCanceled(generation.getId())) {
                audit("GENERATE_CANCELED", request.getProjectId(), userId, "generationId=" + generation.getId());
                FunctionalCaseAiGenerateResponse canceled = new FunctionalCaseAiGenerateResponse();
                canceled.setGenerationId(generation.getId());
                canceled.setCreatedCount(0);
                canceled.setDrafts(Collections.emptyList());
                canceled.setWarnings(List.of("生成任务已取消"));
                return canceled;
            }
            generation.setStatus(FunctionalCaseAiGenerationStatus.FAILED.name());
            generation.setDurationMs(System.currentTimeMillis() - startTime);
            generation.setErrorMessage(StringUtils.left(ex.getMessage(), 4000));
            generation.setUpdateTime(System.currentTimeMillis());
            generationMapper.updateTerminalIfActive(generation);
            audit("GENERATE_FAILED", request.getProjectId(), userId, "generationId=" + generation.getId() + ",error=" + ex.getMessage());
            if (ex instanceof MSException msException) {
                throw msException;
            }
            throw new MSException("AI 结构化生成失败：" + ex.getMessage(), ex);
        }
    }

    public void cancel(FunctionalCaseAiGenerationCancelRequest request, String userId) {
        FunctionalCaseAiGeneration generation = generationMapper.selectByPrimaryKey(request.getGenerationId());
        if (generation == null
                || !StringUtils.equals(generation.getProjectId(), request.getProjectId())
                || !StringUtils.equals(generation.getCreateUser(), userId)) {
            throw new MSException("生成任务不存在或无权限");
        }
        if (FunctionalCaseAiGenerationStatus.GENERATED.name().equals(generation.getStatus())
                || FunctionalCaseAiGenerationStatus.FAILED.name().equals(generation.getStatus())
                || FunctionalCaseAiGenerationStatus.CANCELED.name().equals(generation.getStatus())) {
            return;
        }
        if (generationMapper.cancelIfActive(generation.getId(), request.getProjectId(), userId,
                System.currentTimeMillis(), "用户取消生成") > 0) {
            audit("GENERATE_CANCEL", request.getProjectId(), userId, "generationId=" + generation.getId());
        }
    }

    private boolean isCanceled(String generationId) {
        FunctionalCaseAiGeneration latest = generationMapper.selectByPrimaryKey(generationId);
        return latest != null && FunctionalCaseAiGenerationStatus.CANCELED.name().equals(latest.getStatus());
    }

    public FunctionalCaseAiDraftPageResponse page(FunctionalCaseAiDraftPageRequest request, String userId) {
        int current = Math.max(1, request.getCurrent() == null ? 1 : request.getCurrent());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize() == null ? 20 : request.getPageSize()));
        String status = StringUtils.equalsIgnoreCase(request.getDraftStatus(), "ALL") ? null : request.getDraftStatus();
        long total = draftMapper.countByProjectAndCreateUser(request.getProjectId(), userId, status);
        List<FunctionalCaseAiDraft> rows = draftMapper.selectByProjectAndCreateUser(
                request.getProjectId(), userId, status, (long) (current - 1) * pageSize, pageSize);
        FunctionalCaseAiDraftPageResponse response = new FunctionalCaseAiDraftPageResponse();
        response.setTotal(total);
        response.setRecords(rows.stream().map(this::toDTO).toList());
        return response;
    }

    public FunctionalCaseAiDraftPageResponse reviewQueue(FunctionalCaseAiDraftPageRequest request) {
        int current = Math.max(1, request.getCurrent() == null ? 1 : request.getCurrent());
        int pageSize = Math.min(100, Math.max(1, request.getPageSize() == null ? 20 : request.getPageSize()));
        String status = StringUtils.equalsIgnoreCase(request.getDraftStatus(), "ALL") ? null : request.getDraftStatus();
        FunctionalCaseAiDraftPageResponse response = new FunctionalCaseAiDraftPageResponse();
        response.setTotal(draftMapper.countReviewQueue(request.getProjectId(), status));
        response.setRecords(draftMapper.selectReviewQueue(request.getProjectId(), status,
                (long) (current - 1) * pageSize, pageSize).stream().map(this::toDTO).toList());
        return response;
    }

    public FunctionalCaseAiDraftDTO get(String id, String projectId, String userId) {
        FunctionalCaseAiDraft draft = requireDraft(id, projectId, userId);
        return toDTO(draft);
    }

    public FunctionalCaseAiDraftDTO update(FunctionalCaseAiDraftUpsertRequest request, String userId) {
        FunctionalCaseAiDraft existing = requireDraft(request.getId(), request.getProjectId(), userId);
        if (FunctionalCaseAiDraftStatus.SAVED.name().equals(existing.getDraftStatus())) {
            throw new MSException("已保存为正式用例的草稿不允许继续编辑");
        }
        FunctionalCaseAiDraft update = new FunctionalCaseAiDraft();
        update.setId(existing.getId());
        update.setSourceDocumentId(request.getSourceDocumentId());
        update.setModuleId(request.getModuleId());
        update.setTemplateId(request.getTemplateId());
        update.setName(request.getName());
        update.setCaseLevel(normalizeLevel(request.getCaseLevel()));
        update.setEditType(normalizeEditType(request.getEditType()));
        update.setPrerequisite(request.getPrerequisite());
        update.setSteps(request.getSteps());
        update.setExpectedResult(request.getExpectedResult());
        update.setTags(request.getTags());
        update.setCustomFields(request.getCustomFields());
        update.setDraftStatus(FunctionalCaseAiDraftStatus.DRAFT.name());
        update.setReviewStatus(FunctionalCaseAiDraftStatus.PENDING_REVIEW.name());
        update.setPublishMode(normalizePublishMode(request.getPublishMode()));
        update.setTargetCaseId(StringUtils.trimToNull(request.getTargetCaseId()));
        update.setBaselineSnapshot(buildTargetBaseline(request.getProjectId(), update.getPublishMode(), update.getTargetCaseId()));
        update.setUpdateTime(System.currentTimeMillis());
        validateDraft(update, existing.getId(), request.getProjectId(), userId);
        update.setContentHash(contentHash(update));
        int affected = draftMapper.updateByPrimaryKeyAndVersionSelective(update, request.getVersion());
        if (affected == 0) {
            throw new MSException("草稿已被其他窗口更新，请刷新后重试");
        }
        audit("EDIT_DRAFT", request.getProjectId(), userId, "draftId=" + existing.getId());
        return toDTO(draftMapper.selectByPrimaryKey(existing.getId()));
    }

    public void delete(FunctionalCaseAiDraftBatchDeleteRequest request, String userId) {
        long now = System.currentTimeMillis();
        for (String id : request.getDraftIds()) {
            draftMapper.markDeleted(id, request.getProjectId(), userId, now);
        }
        audit("DELETE_DRAFT", request.getProjectId(), userId, "draftIds=" + request.getDraftIds());
    }

    public List<FunctionalCaseAiDraftDTO> review(FunctionalCaseAiDraftReviewRequest request, String reviewer) {
        String action = StringUtils.upperCase(StringUtils.trim(request.getAction()));
        String reviewStatus = switch (action) {
            case "SUBMIT" -> FunctionalCaseAiDraftStatus.PENDING_REVIEW.name();
            case "APPROVE" -> FunctionalCaseAiDraftStatus.APPROVED.name();
            case "REQUEST_CHANGES" -> FunctionalCaseAiDraftStatus.CHANGES_REQUESTED.name();
            case "REJECT" -> FunctionalCaseAiDraftStatus.REJECTED.name();
            default -> throw new MSException("review action 仅支持 SUBMIT/APPROVE/REQUEST_CHANGES/REJECT");
        };
        if (List.of("REQUEST_CHANGES", "REJECT").contains(action) && StringUtils.isBlank(request.getComment())) {
            throw new MSException("退回或驳回必须填写审核意见");
        }
        Map<String, FunctionalCaseAiDraft> byId = new HashMap<>();
        draftMapper.selectByIdsInProject(request.getDraftIds(), request.getProjectId())
                .forEach(draft -> byId.put(draft.getId(), draft));
        List<FunctionalCaseAiDraftDTO> results = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String id : request.getDraftIds()) {
            FunctionalCaseAiDraft draft = byId.get(id);
            if (draft == null) {
                throw new MSException("草稿不存在或不属于当前项目: " + id);
            }
            validateDraft(draft);
            if ("APPROVE".equals(action) && FunctionalCaseAiDraftStatus.INVALID.name().equals(draft.getValidationStatus())) {
                throw new MSException("校验未通过的草稿不能审核通过: " + draft.getName());
            }
            String hash = contentHash(draft);
            int updated = draftMapper.updateReview(id, request.getProjectId(), draft.getVersion(), reviewStatus,
                    StringUtils.abbreviate(StringUtils.trimToNull(request.getComment()), 2000), reviewer, now,
                    "APPROVE".equals(action) ? hash : null, now);
            if (updated != 1) {
                throw new MSException("草稿已被修改，请刷新后重新审核: " + draft.getName());
            }
            draftMapper.insertReviewHistory(IDGenerator.nextStr(), id, request.getProjectId(), action,
                    StringUtils.abbreviate(StringUtils.trimToNull(request.getComment()), 2000), hash, reviewer, now);
            results.add(toDTO(draftMapper.selectByPrimaryKey(id)));
        }
        audit("REVIEW_" + action, request.getProjectId(), reviewer, "draftIds=" + request.getDraftIds());
        return results;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FunctionalCaseAiGenerateResponse regenerate(FunctionalCaseAiDraftRegenerateRequest request, String userId) {
        FunctionalCaseAiDraft draft = requireDraft(request.getDraftId(), request.getProjectId(), userId);
        FunctionalCaseAiGenerateRequest generateRequest = new FunctionalCaseAiGenerateRequest();
        generateRequest.setProjectId(request.getProjectId());
        generateRequest.setModuleId(draft.getModuleId());
        generateRequest.setTemplateId(draft.getTemplateId());
        generateRequest.setChatModelId(request.getChatModelId());
        generateRequest.setConversationId(request.getConversationId());
        generateRequest.setOrganizationId(request.getOrganizationId());
        generateRequest.setMaxCases(1);
        generateRequest.setPrompt(buildRegeneratePrompt(draft, request.getPrompt()));
        return generate(generateRequest, userId);
    }

    public FunctionalCaseAiBatchSaveResponse batchSave(FunctionalCaseAiDraftBatchSaveRequest request,
                                                       String userId,
                                                       String organizationId) {
        List<FunctionalCaseAiDraft> drafts = draftMapper.selectByIdsInProject(request.getDraftIds(), request.getProjectId());
        Map<String, FunctionalCaseAiDraft> draftMap = new HashMap<>();
        drafts.forEach(draft -> draftMap.put(draft.getId(), draft));
        FunctionalCaseAiBatchSaveResponse response = new FunctionalCaseAiBatchSaveResponse();
        for (String draftId : request.getDraftIds()) {
            FunctionalCaseAiBatchSaveResponse.ItemResult itemResult = new FunctionalCaseAiBatchSaveResponse.ItemResult();
            itemResult.setDraftId(draftId);
            boolean publishStarted = false;
            try {
                FunctionalCaseAiDraft draft = draftMap.get(draftId);
                if (draft == null) {
                    throw new MSException("草稿不存在或无权限访问");
                }
                itemResult.setName(draft.getName());
                validatePublishPreconditions(draft);
                publishStarted = true;
                TransactionTemplate itemTransaction = new TransactionTemplate(transactionManager);
                itemTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                FunctionalCase created = itemTransaction.execute(status ->
                        saveOneDraftAsCase(draft, request, userId, organizationId));
                if (created == null) {
                    throw new MSException("正式用例保存事务未返回结果");
                }
                itemResult.setFormalCaseId(created.getId());
                itemResult.setSuccess(true);
                response.setSuccessCount(response.getSuccessCount() + 1);
            } catch (Exception ex) {
                itemResult.setSuccess(false);
                itemResult.setMessage(ex.getMessage());
                response.setFailureCount(response.getFailureCount() + 1);
                if (publishStarted) {
                    markDraftFailed(draftMap.get(draftId), ex.getMessage());
                }
            }
            response.getResults().add(itemResult);
        }
        audit("BATCH_SAVE", request.getProjectId(), userId, JSON.toJSONString(response));
        return response;
    }

    private FunctionalCase saveOneDraftAsCase(FunctionalCaseAiDraft draft,
                                              FunctionalCaseAiDraftBatchSaveRequest request,
                                              String userId,
                                              String organizationId) {
        validatePublishPreconditions(draft);
        String moduleId = StringUtils.defaultIfBlank(draft.getModuleId(), request.getModuleId());
        moduleId = StringUtils.defaultIfBlank(moduleId, ModuleConstants.DEFAULT_NODE_ID);
        String templateId = StringUtils.defaultIfBlank(draft.getTemplateId(), request.getTemplateId());
        if (StringUtils.isBlank(templateId)) {
            TemplateDTO templateDTO = projectTemplateService.getDefaultTemplateDTO(draft.getProjectId(), TemplateScene.FUNCTIONAL.name());
            if (templateDTO == null || StringUtils.isBlank(templateDTO.getId())) {
                throw new MSException("项目未配置功能用例默认模板");
            }
            templateId = templateDTO.getId();
        }
        Project project = projectMapper.selectByPrimaryKey(draft.getProjectId());
        String resolvedOrganizationId = StringUtils.defaultIfBlank(organizationId, project == null ? null : project.getOrganizationId());
        if (StringUtils.isBlank(resolvedOrganizationId)) {
            throw new MSException("无法获取项目所属组织");
        }

        FunctionalCaseAiDraft saving = new FunctionalCaseAiDraft();
        saving.setId(draft.getId());
        saving.setDraftStatus(FunctionalCaseAiDraftStatus.SAVING.name());
        saving.setUpdateTime(System.currentTimeMillis());
        draftMapper.updateByPrimaryKeySelective(saving);

        FunctionalCaseAddRequest addRequest = new FunctionalCaseAddRequest();
        addRequest.setProjectId(draft.getProjectId());
        addRequest.setModuleId(moduleId);
        addRequest.setTemplateId(templateId);
        addRequest.setName(draft.getName());
        addRequest.setPrerequisite(StringUtils.defaultString(draft.getPrerequisite()));
        addRequest.setCaseEditType(normalizeEditType(draft.getEditType()));
        addRequest.setSteps(StringUtils.defaultIfBlank(draft.getSteps(), JSON.toJSONString(new ArrayList<FunctionalCaseStepDTO>())));
        addRequest.setTextDescription(FunctionalCaseTypeConstants.CaseEditType.TEXT.name().equals(normalizeEditType(draft.getEditType()))
                ? StringUtils.defaultString(draft.getSteps())
                : StringUtils.EMPTY);
        addRequest.setExpectedResult(StringUtils.defaultString(draft.getExpectedResult()));
        addRequest.setTags(parseTags(draft.getTags()));
        addRequest.setCustomFields(parseCustomFields(draft.getCustomFields()));
        addRequest.setAiCreate(true);
        FunctionalCase created = functionalCaseService.addFunctionalCase(addRequest, new ArrayList<>(), userId, resolvedOrganizationId);

        applicationEventPublisher.publishEvent(new TestAssetCasePublishedEvent(
                draft, created, JSON.toJSONString(Map.of(
                "case", created,
                "prerequisite", StringUtils.defaultString(draft.getPrerequisite()),
                "steps", StringUtils.defaultString(draft.getSteps()),
                "expectedResult", StringUtils.defaultString(draft.getExpectedResult()),
                "sourceReferences", StringUtils.defaultString(draft.getSourceReferences()),
                "generationId", draft.getGenerationId(),
                "draftId", draft.getId())), userId));

        FunctionalCaseAiDraft saved = new FunctionalCaseAiDraft();
        saved.setId(draft.getId());
        saved.setFormalCaseId(created.getId());
        saved.setDraftStatus(FunctionalCaseAiDraftStatus.SAVED.name());
        saved.setValidationStatus(FunctionalCaseAiDraftStatus.READY.name());
        saved.setUpdateTime(System.currentTimeMillis());
        draftMapper.updateByPrimaryKeySelective(saved);
        return created;
    }

    private void validatePublishPreconditions(FunctionalCaseAiDraft draft) {
        if (FunctionalCaseAiDraftStatus.SAVED.name().equals(draft.getDraftStatus())) {
            throw new MSException("草稿已保存为正式用例");
        }
        String currentHash = contentHash(draft);
        if (!FunctionalCaseAiDraftStatus.APPROVED.name().equals(draft.getReviewStatus())
                || !StringUtils.equals(currentHash, draft.getReviewedContentHash())) {
            throw new MSException("草稿尚未审核通过，或审核后内容已发生变化");
        }
        if (!"CREATE".equals(normalizePublishMode(draft.getPublishMode()))) {
            throw new MSException("当前版本仅允许发布新增建议；修改/废弃建议尚未完成领域版本接入");
        }
        validateDraft(draft);
        if (FunctionalCaseAiDraftStatus.INVALID.name().equals(draft.getValidationStatus())) {
            throw new MSException(StringUtils.defaultIfBlank(draft.getValidationMessage(), "草稿校验未通过"));
        }
    }

    private FunctionalCaseAiGeneration createGeneration(FunctionalCaseAiGenerateRequest request, String userId, long now) {
        FunctionalCaseAiGeneration generation = new FunctionalCaseAiGeneration();
        generation.setId(StringUtils.defaultIfBlank(request.getGenerationId(), IDGenerator.nextStr()));
        generation.setProjectId(request.getProjectId());
        generation.setConversationId(request.getConversationId());
        generation.setModelSourceId(request.getChatModelId());
        generation.setPrompt(request.getPrompt());
        generation.setStatus(FunctionalCaseAiGenerationStatus.GENERATING.name());
        generation.setCreateUser(userId);
        generation.setCreateTime(now);
        generation.setUpdateTime(now);
        return generation;
    }

    public FunctionalCaseAiGenerateResponse createDraftsFromAgent(String projectId, String conversationId,
                                                                  String requestId, String modelSourceId,
                                                                  List<CaseGenerationCaseDTO> cases, String userId) {
        if (CollectionUtils.isEmpty(cases)) {
            throw new MSException("create_case_drafts 至少需要一条用例");
        }
        if (cases.size() > MAX_CASES) {
            throw new MSException("单次最多创建 " + MAX_CASES + " 条草稿");
        }
        for (int index = 0; index < cases.size(); index++) {
            validateAgentCaseArgument(cases.get(index), index);
        }
        long now = System.currentTimeMillis();
        String generationId = IDGenerator.nextStr();
        FunctionalCaseAiGenerateRequest buildRequest = new FunctionalCaseAiGenerateRequest();
        buildRequest.setProjectId(projectId);
        buildRequest.setConversationId(conversationId);
        buildRequest.setChatModelId(modelSourceId);
        buildRequest.setPrompt("Agent tool create_case_drafts requestId=" + requestId);

        FunctionalCaseAiGeneration generation = new FunctionalCaseAiGeneration();
        generation.setId(generationId);
        generation.setProjectId(projectId);
        generation.setConversationId(conversationId);
        generation.setModelSourceId(modelSourceId);
        generation.setPrompt("Agent tool create_case_drafts");
        generation.setStatus(FunctionalCaseAiGenerationStatus.GENERATED.name());
        generation.setTokenUsage(0L);
        generation.setDurationMs(0L);
        generation.setCreateUser(userId);
        generation.setCreateTime(now);
        generation.setUpdateTime(now);

        List<FunctionalCaseAiDraft> entities = new ArrayList<>();
        for (CaseGenerationCaseDTO item : cases) {
            FunctionalCaseAiDraft draft = buildDraft(buildRequest, item, generationId, userId);
            draft.setConversationId(conversationId);
            draft.setRequestId(requestId);
            validateDraft(draft);
            if (FunctionalCaseAiDraftStatus.INVALID.name().equals(draft.getValidationStatus())) {
                throw new MSException("草稿参数校验失败：" + draft.getValidationMessage());
            }
            entities.add(draft);
        }
        generationMapper.insert(generation);
        entities.forEach(draftMapper::insert);

        FunctionalCaseAiGenerateResponse response = new FunctionalCaseAiGenerateResponse();
        response.setGenerationId(generationId);
        response.setCreatedCount(entities.size());
        response.setDrafts(entities.stream().map(this::toDTO).toList());
        response.setWarnings(entities.stream().filter(item -> Boolean.TRUE.equals(item.getDuplicate()))
                .map(item -> "草稿“" + item.getName() + "”可能重复").toList());
        audit("AGENT_CREATE_DRAFTS", projectId, userId,
                "requestId=" + requestId + ",drafts=" + entities.size());
        return response;
    }

    /**
     * Reuses the model API structured-output parser for the personal Agent channel.
     * It performs at most the existing bounded compatibility repair and never
     * persists data; callers must still use {@link #createDraftsFromAgent}.
     */
    public CaseGenerationResult parseAgentGenerationResult(String rawContent) {
        return parseGenerationResult(rawContent);
    }

    private void validateAgentCaseArgument(CaseGenerationCaseDTO item, int index) {
        String path = "cases[" + index + "]";
        if (item == null || StringUtils.isBlank(item.getName())) {
            throw new MSException(path + ".name 不能为空");
        }
        String level = StringUtils.upperCase(item.getLevel());
        if (!List.of("P0", "P1", "P2", "P3").contains(level)) {
            throw new MSException(path + ".level 必须为 P0/P1/P2/P3");
        }
        String editType = StringUtils.upperCase(item.getEditType());
        if (!List.of("STEP", "TEXT").contains(editType)) {
            throw new MSException(path + ".editType 必须为 STEP/TEXT");
        }
        if ("STEP".equals(editType)) {
            if (CollectionUtils.isEmpty(item.getSteps())) {
                throw new MSException(path + ".steps 不能为空");
            }
            for (int stepIndex = 0; stepIndex < item.getSteps().size(); stepIndex++) {
                FunctionalCaseStepDTO step = item.getSteps().get(stepIndex);
                if (step == null || StringUtils.isBlank(step.getDesc())) {
                    throw new MSException(path + ".steps[" + stepIndex + "].desc 不能为空");
                }
            }
        } else if (StringUtils.isBlank(item.getTextDescription())) {
            throw new MSException(path + ".textDescription 不能为空");
        }
    }

    private AiProviderInvocationResult callAiForStructuredCases(FunctionalCaseAiGenerateRequest request, String userId, int maxCases) {
        String system = """
                你是 MeterSphere 功能测试用例生成助手。必须只返回 JSON，不要返回 Markdown 或解释。
                JSON Schema:
                {"cases":[{"name":"必填","level":"P0|P1|P2|P3","editType":"STEP|TEXT","prerequisite":"","steps":[{"num":1,"desc":"步骤","result":"预期结果","sourceRef":"可选来源"}],"textDescription":"","expectedResult":"","tags":["标签"],"sourceReferences":[{"documentId":"","section":"","excerpt":""}]}],"warnings":[]}
                限制：最多返回 %s 条；不得输出 projectId、createUser、token、密钥、Cookie 或其它敏感字段。
                """.formatted(maxCases);
        String fullPrompt = "请根据以下材料生成结构化功能测试用例：\n"
                + buildSourceDocumentContext(request, userId)
                + "\n用户输入：\n" + request.getPrompt();
        aiChatBaseService.saveUserConversationContent(request.getConversationId(), fullPrompt);
        AiProviderChatRequest providerRequest = new AiProviderChatRequest();
        providerRequest.setProjectId(request.getProjectId());
        providerRequest.setChatModelId(request.getChatModelId());
        providerRequest.setConversationId(request.getConversationId());
        providerRequest.setOrganizationId(request.getOrganizationId());
        providerRequest.setSystem(system);
        providerRequest.setPrompt(fullPrompt);
        AiProviderInvocationResult result = aiProviderAdapter.invokeAdmitted(providerRequest, userId);
        aiChatBaseService.saveAssistantConversationContent(request.getConversationId(), result.getContent());
        return result;
    }

    private FunctionalCaseAiGenerateResponse canceledResponse(String generationId) {
        FunctionalCaseAiGenerateResponse canceled = new FunctionalCaseAiGenerateResponse();
        canceled.setGenerationId(generationId);
        canceled.setCreatedCount(0);
        canceled.setDrafts(Collections.emptyList());
        canceled.setWarnings(List.of("生成任务已取消"));
        return canceled;
    }

    private String buildSourceDocumentContext(FunctionalCaseAiGenerateRequest request, String userId) {
        if (CollectionUtils.isEmpty(request.getSourceDocumentIds())) {
            return StringUtils.EMPTY;
        }
        List<AiSourceDocument> documents = aiSourceDocumentMapper.selectByIdsInProject(
                request.getSourceDocumentIds(), request.getProjectId());
        if (CollectionUtils.isEmpty(documents)) {
            return StringUtils.EMPTY;
        }
        StringBuilder builder = new StringBuilder("已解析产品方案上下文：\n");
        for (AiSourceDocument document : documents) {
            builder.append("文件：").append(document.getOriginalName()).append('\n')
                    .append("摘要：").append(StringUtils.defaultString(document.getSummary())).append('\n')
                    .append("章节：").append(StringUtils.left(StringUtils.defaultString(document.getSectionIndex()), 3000)).append("\n\n");
        }
        return builder.toString();
    }

    private CaseGenerationResult parseGenerationResult(String rawContent) {
        if (StringUtils.isBlank(rawContent)) {
            throw new MSException("AI 返回为空");
        }
        try {
            String json = extractJson(rawContent);
            CaseGenerationJsonSchemaValidator.validateOrThrow(json);
            return JSON.parseObject(json, CaseGenerationResult.class);
        } catch (Exception first) {
            List<FunctionalCaseAiDTO> mdCases = MdUtil.batchTransformToCaseDTO(rawContent);
            if (CollectionUtils.isEmpty(mdCases)) {
                throw new MSException("AI 返回内容不符合结构化 JSON，且修复失败：" + first.getMessage());
            }
            CaseGenerationResult repaired = new CaseGenerationResult();
            repaired.setCases(mdCases.stream().map(this::fromLegacyAiDTO).toList());
            repaired.setWarnings(new ArrayList<>());
            repaired.getWarnings().add("AI 未返回标准 JSON，已按旧 Markdown 格式尝试修复");
            try {
                CaseGenerationJsonSchemaValidator.validateOrThrow(JSON.toJSONString(repaired));
            } catch (Exception schemaEx) {
                repaired.getWarnings().add("结构修复后 Schema 仍有告警：" + schemaEx.getMessage());
            }
            return repaired;
        }
    }

    private String extractJson(String rawContent) {
        String content = StringUtils.trim(rawContent);
        if (content.startsWith("```")) {
            int firstLineEnd = content.indexOf('\n');
            int lastFence = content.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                content = content.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        int objectStart = content.indexOf('{');
        int objectEnd = content.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return content.substring(objectStart, objectEnd + 1);
        }
        return content;
    }

    private CaseGenerationCaseDTO fromLegacyAiDTO(FunctionalCaseAiDTO source) {
        CaseGenerationCaseDTO target = new CaseGenerationCaseDTO();
        target.setName(source.getName());
        target.setEditType(StringUtils.defaultIfBlank(source.getCaseEditType(), FunctionalCaseTypeConstants.CaseEditType.STEP.name()));
        target.setPrerequisite(source.getPrerequisite());
        target.setTextDescription(source.getTextDescription());
        target.setExpectedResult(source.getExpectedResult());
        FunctionalCaseStepDTO step = new FunctionalCaseStepDTO();
        step.setId(IDGenerator.nextStr());
        step.setNum(1);
        step.setDesc(StringUtils.defaultString(source.getSteps()));
        step.setResult(StringUtils.defaultString(source.getExpectedResult()));
        target.setSteps(new ArrayList<>(List.of(step)));
        return target;
    }

    private List<CaseGenerationCaseDTO> sanitizeAndLimit(CaseGenerationResult result, int maxCases) {
        if (result == null || CollectionUtils.isEmpty(result.getCases())) {
            return Collections.emptyList();
        }
        if (result.getWarnings() == null) {
            result.setWarnings(new ArrayList<>());
        }
        List<CaseGenerationCaseDTO> cases = new ArrayList<>(result.getCases());
        if (cases.size() > maxCases) {
            cases = new ArrayList<>(cases.subList(0, maxCases));
            result.getWarnings().add("AI 返回数量超过限制，已截断为 " + maxCases + " 条");
        }
        cases.sort(Comparator.comparing(item -> StringUtils.defaultString(item.getName())));
        return cases;
    }

    private FunctionalCaseAiDraft buildDraft(FunctionalCaseAiGenerateRequest request,
                                             CaseGenerationCaseDTO item,
                                             String generationId,
                                             String userId) {
        FunctionalCaseAiDraft draft = new FunctionalCaseAiDraft();
        long now = System.currentTimeMillis();
        draft.setId(IDGenerator.nextStr());
        draft.setGenerationId(generationId);
        draft.setProjectId(request.getProjectId());
        if (CollectionUtils.isNotEmpty(request.getSourceDocumentIds())) {
            draft.setSourceDocumentId(request.getSourceDocumentIds().getFirst());
        }
        draft.setModuleId(StringUtils.defaultIfBlank(item.getModuleId(), request.getModuleId()));
        draft.setTemplateId(StringUtils.defaultIfBlank(item.getTemplateId(), request.getTemplateId()));
        draft.setName(StringUtils.trim(item.getName()));
        draft.setCaseLevel(normalizeLevel(item.getLevel()));
        draft.setEditType(normalizeEditType(item.getEditType()));
        draft.setPrerequisite(StringUtils.defaultString(item.getPrerequisite()));
        draft.setSteps(FunctionalCaseTypeConstants.CaseEditType.TEXT.name().equals(draft.getEditType())
                ? StringUtils.defaultString(item.getTextDescription())
                : toStepsJson(item.getSteps()));
        draft.setExpectedResult(StringUtils.defaultString(item.getExpectedResult()));
        draft.setTags(JSON.toJSONString(CollectionUtils.emptyIfNull(item.getTags())));
        draft.setCustomFields(JSON.toJSONString(new ArrayList<CaseCustomFieldDTO>()));
        draft.setSourceReferences(JSON.toJSONString(CollectionUtils.emptyIfNull(item.getSourceReferences())));
        draft.setDraftStatus(FunctionalCaseAiDraftStatus.DRAFT.name());
        draft.setReviewStatus(FunctionalCaseAiDraftStatus.PENDING_REVIEW.name());
        draft.setPublishMode("CREATE");
        draft.setDeleted(false);
        draft.setVersion(0);
        draft.setCreateUser(userId);
        draft.setCreateTime(now);
        draft.setUpdateTime(now);
        draft.setContentHash(contentHash(draft));
        return draft;
    }

    private String toStepsJson(List<FunctionalCaseStepDTO> steps) {
        List<FunctionalCaseStepDTO> normalized = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(steps)) {
            int index = 1;
            for (FunctionalCaseStepDTO step : steps) {
                if (StringUtils.isBlank(step.getId())) {
                    step.setId(IDGenerator.nextStr());
                }
                if (step.getNum() == null) {
                    step.setNum(index);
                }
                normalized.add(step);
                index++;
            }
        }
        return JSON.toJSONString(normalized);
    }

    private void validateDraft(FunctionalCaseAiDraft draft) {
        validateDraft(draft, draft.getId(), draft.getProjectId(), draft.getCreateUser());
    }

    private void validateDraft(FunctionalCaseAiDraft draft, String excludeId, String projectId, String userId) {
        List<String> errors = new ArrayList<>();
        if (StringUtils.isBlank(draft.getName())) {
            errors.add("用例名称不能为空");
        }
        if (!List.of("P0", "P1", "P2", "P3").contains(normalizeLevel(draft.getCaseLevel()))) {
            errors.add("用例等级必须为 P0/P1/P2/P3");
        }
        if (!List.of("STEP", "TEXT").contains(normalizeEditType(draft.getEditType()))) {
            errors.add("编辑模式必须为 STEP/TEXT");
        }
        if (FunctionalCaseTypeConstants.CaseEditType.STEP.name().equals(normalizeEditType(draft.getEditType()))
                && !isJsonArray(draft.getSteps())) {
            errors.add("步骤必须为 JSON 数组");
        }
        String fingerprint = fingerprint(projectId, draft.getName());
        boolean duplicate = false;
        if (StringUtils.isNotBlank(fingerprint)) {
            duplicate = draftMapper.countDuplicateByFingerprint(projectId, userId, fingerprint, excludeId) > 0
                    || existsFormalCase(projectId, draft.getName());
        }
        draft.setFingerprint(fingerprint);
        draft.setDuplicate(duplicate);
        List<String> warnings = new ArrayList<>();
        if (duplicate) {
            warnings.add("存在同名或疑似重复用例，确认后仍可保存");
        }
        List<String> messages = new ArrayList<>();
        messages.addAll(errors);
        messages.addAll(warnings);
        draft.setValidationMessage(String.join("; ", messages));
        draft.setValidationStatus(errors.isEmpty()
                ? FunctionalCaseAiDraftStatus.READY.name()
                : FunctionalCaseAiDraftStatus.INVALID.name());
    }

    private boolean existsFormalCase(String projectId, String name) {
        if (StringUtils.isAnyBlank(projectId, name)) {
            return false;
        }
        FunctionalCaseExample example = new FunctionalCaseExample();
        example.createCriteria()
                .andProjectIdEqualTo(projectId)
                .andNameEqualTo(name)
                .andDeletedEqualTo(false);
        return functionalCaseMapper.countByExample(example) > 0;
    }

    private String fingerprint(String projectId, String name) {
        if (StringUtils.isAnyBlank(projectId, name)) {
            return null;
        }
        return DigestUtils.sha256Hex(projectId + ":" + StringUtils.deleteWhitespace(name).toLowerCase());
    }

    private String normalizePublishMode(String mode) {
        String normalized = StringUtils.defaultIfBlank(StringUtils.upperCase(mode), "CREATE");
        if (!List.of("CREATE", "UPDATE", "DEPRECATE").contains(normalized)) {
            throw new MSException("publishMode 仅支持 CREATE/UPDATE/DEPRECATE");
        }
        return normalized;
    }

    private String buildTargetBaseline(String projectId, String mode, String targetCaseId) {
        if ("CREATE".equals(mode)) {
            return null;
        }
        if (StringUtils.isBlank(targetCaseId)) {
            throw new MSException(mode + " 建议必须选择目标正式用例");
        }
        FunctionalCase target = functionalCaseMapper.selectByPrimaryKey(targetCaseId);
        if (target == null || !StringUtils.equals(projectId, target.getProjectId())
                || Boolean.TRUE.equals(target.getDeleted()) || !Boolean.TRUE.equals(target.getLatest())) {
            throw new MSException("目标正式用例不存在、已删除或不属于当前项目");
        }
        return JSON.toJSONString(target);
    }

    private String contentHash(FunctionalCaseAiDraft draft) {
        Map<String, Object> content = new java.util.LinkedHashMap<>();
        content.put("moduleId", draft.getModuleId());
        content.put("templateId", draft.getTemplateId());
        content.put("name", draft.getName());
        content.put("caseLevel", draft.getCaseLevel());
        content.put("editType", draft.getEditType());
        content.put("prerequisite", draft.getPrerequisite());
        content.put("steps", draft.getSteps());
        content.put("expectedResult", draft.getExpectedResult());
        content.put("tags", draft.getTags());
        content.put("customFields", draft.getCustomFields());
        content.put("sourceReferences", draft.getSourceReferences());
        content.put("publishMode", normalizePublishMode(draft.getPublishMode()));
        content.put("targetCaseId", draft.getTargetCaseId());
        return DigestUtils.sha256Hex(JSON.toJSONString(content));
    }

    private boolean isJsonArray(String value) {
        if (StringUtils.isBlank(value)) {
            return true;
        }
        try {
            JSON.parseArray(value, FunctionalCaseStepDTO.class);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private String normalizeLevel(String level) {
        String normalized = StringUtils.upperCase(StringUtils.defaultIfBlank(level, "P1"));
        return List.of("P0", "P1", "P2", "P3").contains(normalized) ? normalized : "P1";
    }

    private String normalizeEditType(String editType) {
        String normalized = StringUtils.upperCase(StringUtils.defaultIfBlank(editType, FunctionalCaseTypeConstants.CaseEditType.STEP.name()));
        return FunctionalCaseTypeConstants.CaseEditType.TEXT.name().equals(normalized)
                ? FunctionalCaseTypeConstants.CaseEditType.TEXT.name()
                : FunctionalCaseTypeConstants.CaseEditType.STEP.name();
    }

    private List<String> parseTags(String raw) {
        if (StringUtils.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(raw, String.class);
        } catch (Exception ex) {
            return Arrays.stream(raw.split(",")).map(StringUtils::trim).filter(StringUtils::isNotBlank).toList();
        }
    }

    private List<CaseCustomFieldDTO> parseCustomFields(String raw) {
        if (StringUtils.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(raw, CaseCustomFieldDTO.class);
        } catch (Exception ex) {
            throw new MSException("自定义字段必须为 JSON 数组");
        }
    }

    private int normalizeMaxCases(Integer maxCases) {
        if (maxCases == null || maxCases <= 0) {
            return DEFAULT_MAX_CASES;
        }
        return Math.min(maxCases, MAX_CASES);
    }

    private FunctionalCaseAiDraft requireDraft(String id, String projectId, String userId) {
        FunctionalCaseAiDraft draft = draftMapper.selectByPrimaryKey(id);
        if (draft == null
                || Boolean.TRUE.equals(draft.getDeleted())
                || !StringUtils.equals(projectId, draft.getProjectId())
                || !StringUtils.equals(userId, draft.getCreateUser())) {
            throw new MSException("草稿不存在或无权限访问");
        }
        return draft;
    }

    private void markDraftFailed(FunctionalCaseAiDraft draft, String message) {
        if (draft == null) {
            return;
        }
        FunctionalCaseAiDraft failed = new FunctionalCaseAiDraft();
        failed.setId(draft.getId());
        failed.setDraftStatus(FunctionalCaseAiDraftStatus.FAILED.name());
        failed.setValidationMessage(StringUtils.left(message, 4000));
        failed.setUpdateTime(System.currentTimeMillis());
        draftMapper.updateByPrimaryKeySelective(failed);
    }

    private String buildRegeneratePrompt(FunctionalCaseAiDraft draft, String extraPrompt) {
        return """
                请基于以下草稿重新生成 1 条改进后的功能测试用例：
                名称：%s
                前置条件：%s
                步骤：%s
                预期结果：%s
                额外要求：%s
                """.formatted(draft.getName(), draft.getPrerequisite(), draft.getSteps(), draft.getExpectedResult(),
                StringUtils.defaultString(extraPrompt));
    }

    private FunctionalCaseAiDraftDTO toDTO(FunctionalCaseAiDraft draft) {
        FunctionalCaseAiDraftDTO dto = new FunctionalCaseAiDraftDTO();
        dto.setId(draft.getId());
        dto.setGenerationId(draft.getGenerationId());
        dto.setSourceDocumentId(draft.getSourceDocumentId());
        dto.setProjectId(draft.getProjectId());
        dto.setModuleId(draft.getModuleId());
        dto.setTemplateId(draft.getTemplateId());
        dto.setName(draft.getName());
        dto.setCaseLevel(draft.getCaseLevel());
        dto.setEditType(draft.getEditType());
        dto.setPrerequisite(draft.getPrerequisite());
        dto.setSteps(draft.getSteps());
        dto.setExpectedResult(draft.getExpectedResult());
        dto.setTags(draft.getTags());
        dto.setCustomFields(draft.getCustomFields());
        dto.setSourceReferences(draft.getSourceReferences());
        dto.setValidationMessage(draft.getValidationMessage());
        dto.setFingerprint(draft.getFingerprint());
        dto.setDuplicate(draft.getDuplicate());
        dto.setValidationStatus(draft.getValidationStatus());
        dto.setDraftStatus(draft.getDraftStatus());
        dto.setReviewStatus(draft.getReviewStatus());
        dto.setReviewComment(draft.getReviewComment());
        dto.setReviewedBy(draft.getReviewedBy());
        dto.setReviewedAt(draft.getReviewedAt());
        dto.setPublishMode(draft.getPublishMode());
        dto.setTargetCaseId(draft.getTargetCaseId());
        dto.setBaselineSnapshot(draft.getBaselineSnapshot());
        dto.setContentHash(draft.getContentHash());
        dto.setReviewedContentHash(draft.getReviewedContentHash());
        dto.setFormalCaseId(draft.getFormalCaseId());
        dto.setDeleted(draft.getDeleted());
        dto.setVersion(draft.getVersion());
        dto.setCreateUser(draft.getCreateUser());
        dto.setCreateTime(draft.getCreateTime());
        dto.setUpdateTime(draft.getUpdateTime());
        return dto;
    }

    private void audit(String action, String projectId, String userId, String message) {
        log.info("functional_case_ai action={}, projectId={}, userId={}, {}", action, projectId, userId, message);
        try {
            String type = switch (action) {
                case "GENERATE", "BATCH_SAVE" -> OperationLogType.ADD.name();
                case "EDIT_DRAFT" -> OperationLogType.UPDATE.name();
                case "DELETE_DRAFT" -> OperationLogType.DELETE.name();
                default -> OperationLogType.UPDATE.name();
            };
            LogDTO dto = new LogDTO(
                    projectId,
                    null,
                    action,
                    userId,
                    type,
                    OperationLogModule.CASE_MANAGEMENT_CASE_GENERATE,
                    StringUtils.left(message, 255));
            dto.setPath("/functional/case/ai/draft");
            dto.setMethod(HttpMethodConstants.POST.name());
            dto.setOriginalValue(JSON.toJSONBytes(Map.of("action", action, "detail", StringUtils.defaultString(message))));
            operationLogService.add(dto);
        } catch (Exception ex) {
            log.warn("functional_case_ai audit failed action={}, projectId={}, error={}", action, projectId, ex.getMessage());
        }
    }
}
