package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseReviewAssociateRequest;
import io.metersphere.agent.dto.AgentCaseReviewCreateRequest;
import io.metersphere.agent.dto.AgentCaseReviewDTO;
import io.metersphere.functional.domain.CaseReview;
import io.metersphere.functional.mapper.CaseReviewMapper;
import io.metersphere.functional.request.BaseAssociateCaseRequest;
import io.metersphere.functional.request.CaseReviewAssociateRequest;
import io.metersphere.functional.request.CaseReviewRequest;
import io.metersphere.functional.service.CaseReviewService;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class AgentCaseReviewWriteService {
    @Resource private AgentBusinessAccess access;
    @Resource private AgentOperationExecutor operations;
    @Resource private jakarta.validation.Validator validator;
    private static final String PASS_RULE_SINGLE = "SINGLE";

    @Resource
    private CaseReviewService caseReviewService;
    @Resource
    private CaseReviewMapper caseReviewMapper;
    @Resource
    private AgentExecLogService agentExecLogService;

    public AgentCaseReviewDTO create(AgentCaseReviewCreateRequest request) {
        if(!validator.validate(request).isEmpty())throw new MSException("AGENT_VALIDATION_ERROR");
        return operations.execute("case_review.create",request.getRequestId(),request,AgentCaseReviewDTO.class,
            ()->access.project(request.getProjectId(),io.metersphere.sdk.constants.PermissionConstants.CASE_REVIEW_READ_ADD,true,io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE),()->createOnce(request));
    }

    private AgentCaseReviewDTO createOnce(AgentCaseReviewCreateRequest request) {
        if(!validator.validate(request).isEmpty())throw new MSException("AGENT_VALIDATION_ERROR");
        String userId = access.project(request.getProjectId(),io.metersphere.sdk.constants.PermissionConstants.CASE_REVIEW_READ_ADD,true,io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE);
        List<String> reviewers = resolveReviewers(request.getReviewers(), userId);
        access.reviewers(request.getProjectId(),reviewers);
        access.cases(request.getProjectId(),request.getCaseIds());
        access.module(request.getProjectId(),request.getModuleId(),true);

        CaseReviewRequest reviewRequest = new CaseReviewRequest();
        reviewRequest.setProjectId(request.getProjectId());
        reviewRequest.setName(request.getName());
        reviewRequest.setModuleId(StringUtils.defaultIfBlank(request.getModuleId(), ModuleConstants.DEFAULT_NODE_ID));
        reviewRequest.setReviewPassRule(StringUtils.defaultIfBlank(request.getReviewPassRule(), PASS_RULE_SINGLE));
        reviewRequest.setReviewers(reviewers);
        reviewRequest.setDescription(request.getDescription());
        reviewRequest.setTags(request.getTags());

        BaseAssociateCaseRequest associate = new BaseAssociateCaseRequest();
        associate.setProjectId(request.getProjectId());
        associate.setReviewers(reviewers);
        associate.setSelectAll(false);
        associate.setSelectIds(CollectionUtils.isEmpty(request.getCaseIds())
                ? new ArrayList<>()
                : new ArrayList<>(request.getCaseIds()));
        reviewRequest.setBaseAssociateCaseRequest(associate);

        CaseReview review = caseReviewService.addCaseReview(reviewRequest, userId);
        agentExecLogService.audit("CASE_REVIEW_CREATE", review.getId(), JSON.toJSONString(request));

        AgentCaseReviewDTO dto = toDto(review);
        dto.setAssociatedCaseCount(CollectionUtils.size(request.getCaseIds()));
        return dto;
    }

    public void associate(AgentCaseReviewAssociateRequest request) {
        if(!validator.validate(request).isEmpty())throw new MSException("AGENT_VALIDATION_ERROR");
        operations.execute("case_review.associate_cases",request.getRequestId(),request,java.util.Map.class,()->{
            var review=caseReviewMapper.selectByPrimaryKey(request.getReviewId());
            if(review==null || !java.util.Objects.equals(review.getProjectId(),request.getProjectId()))throw AgentBusinessAccess.denied();
            access.project(review.getProjectId(),io.metersphere.sdk.constants.PermissionConstants.CASE_REVIEW_RELEVANCE,true,io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE);
        },()->{associateOnce(request);return java.util.Map.of("ok",true);});
    }

    private void associateOnce(AgentCaseReviewAssociateRequest request) {
        if(!validator.validate(request).isEmpty())throw new MSException("AGENT_VALIDATION_ERROR");
        var review=caseReviewMapper.selectByPrimaryKey(request.getReviewId());
        if(review==null || !java.util.Objects.equals(review.getProjectId(),request.getProjectId()))throw AgentBusinessAccess.denied();
        String userId=access.project(review.getProjectId(),io.metersphere.sdk.constants.PermissionConstants.CASE_REVIEW_RELEVANCE,true,io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE);
        if("ARCHIVED".equals(review.getStatus()))throw new MSException("AGENT_REVIEW_STATE_CONFLICT");
        List<String> reviewers = resolveReviewers(request.getReviewers(), userId);
        access.reviewers(review.getProjectId(),reviewers);
        access.cases(review.getProjectId(),request.getCaseIds());

        CaseReviewAssociateRequest associateRequest = new CaseReviewAssociateRequest();
        associateRequest.setReviewId(request.getReviewId());
        associateRequest.setProjectId(request.getProjectId());
        associateRequest.setReviewers(reviewers);

        BaseAssociateCaseRequest base = new BaseAssociateCaseRequest();
        base.setProjectId(request.getProjectId());
        base.setReviewers(reviewers);
        base.setSelectAll(false);
        base.setSelectIds(new ArrayList<>(request.getCaseIds()));
        associateRequest.setBaseAssociateCaseRequest(base);

        caseReviewService.associateCase(associateRequest, userId);
        agentExecLogService.audit("CASE_REVIEW_ASSOCIATE", request.getReviewId(), JSON.toJSONString(request));
    }

    public AgentCaseReviewDTO get(String id) {
        CaseReview review = caseReviewMapper.selectByPrimaryKey(id);
        if (review == null) {
            throw AgentBusinessAccess.denied();
        }
        access.project(review.getProjectId(),io.metersphere.sdk.constants.PermissionConstants.CASE_REVIEW_READ,false,io.metersphere.agent.constants.AgentTokenScope.REVIEW_WRITE,io.metersphere.agent.constants.AgentTokenScope.FUNCTIONAL_READ);
        return toDto(review);
    }

    private AgentCaseReviewDTO toDto(CaseReview review) {
        AgentCaseReviewDTO dto = new AgentCaseReviewDTO();
        dto.setId(review.getId());
        dto.setName(review.getName());
        dto.setProjectId(review.getProjectId());
        dto.setStatus(review.getStatus());
        dto.setReviewPassRule(review.getReviewPassRule());
        return dto;
    }

    private List<String> resolveReviewers(List<String> reviewers, String userId) {
        if (CollectionUtils.isEmpty(reviewers)) {
            return Collections.singletonList(userId);
        }
        return reviewers;
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析 Agent Token 对应用户");
        }
        return userId;
    }
}
