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
@Transactional(rollbackFor = Exception.class)
public class AgentCaseReviewWriteService {
    private static final String PASS_RULE_SINGLE = "SINGLE";

    @Resource
    private CaseReviewService caseReviewService;
    @Resource
    private CaseReviewMapper caseReviewMapper;
    @Resource
    private AgentExecLogService agentExecLogService;

    public AgentCaseReviewDTO create(AgentCaseReviewCreateRequest request) {
        String userId = requireUserId();
        List<String> reviewers = resolveReviewers(request.getReviewers(), userId);

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
        String userId = requireUserId();
        List<String> reviewers = resolveReviewers(request.getReviewers(), userId);

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
            throw new MSException("评审不存在: " + id);
        }
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
