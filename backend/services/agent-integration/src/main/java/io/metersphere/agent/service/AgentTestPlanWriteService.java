package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentTestPlanAssociateRequest;
import io.metersphere.agent.dto.AgentTestPlanCreateRequest;
import io.metersphere.agent.dto.AgentTestPlanDTO;
import io.metersphere.plan.domain.TestPlan;
import io.metersphere.plan.dto.request.TestPlanCreateRequest;
import io.metersphere.plan.dto.request.TestPlanFunctionalCaseAssociateRequest;
import io.metersphere.plan.mapper.TestPlanMapper;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.plan.service.TestPlanService;
import io.metersphere.sdk.constants.HttpMethodConstants;
import io.metersphere.sdk.constants.ModuleConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.sdk.SessionUser;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
public class AgentTestPlanWriteService {
    @Resource
    private TestPlanService testPlanService;
    @Resource
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    @Resource
    private TestPlanMapper testPlanMapper;
    @Resource
    private AgentExecLogService agentExecLogService;

    public AgentTestPlanDTO create(AgentTestPlanCreateRequest request) {
        String userId = requireUserId();
        SessionUser user = requireSessionUser();

        TestPlanCreateRequest createRequest = new TestPlanCreateRequest();
        createRequest.setProjectId(request.getProjectId());
        createRequest.setName(request.getName());
        createRequest.setDescription(request.getDescription());
        createRequest.setModuleId(StringUtils.defaultIfBlank(request.getModuleId(), ModuleConstants.DEFAULT_NODE_ID));
        createRequest.setAutomaticStatusUpdate(Boolean.TRUE.equals(request.getAutomaticStatusUpdate()));
        createRequest.setRepeatCase(Boolean.TRUE.equals(request.getRepeatCase()));
        if (request.getPassThreshold() != null) {
            createRequest.setPassThreshold(request.getPassThreshold());
        }

        TestPlan plan = testPlanService.add(createRequest, userId,
                "/api/agent/v1/test-plan/create", HttpMethodConstants.POST.name());

        int associated = 0;
        if (CollectionUtils.isNotEmpty(request.getCaseIds())) {
            AgentTestPlanAssociateRequest associateRequest = new AgentTestPlanAssociateRequest();
            associateRequest.setProjectId(request.getProjectId());
            associateRequest.setTestPlanId(plan.getId());
            associateRequest.setCaseIds(request.getCaseIds());
            associate(associateRequest, user);
            associated = request.getCaseIds().size();
        }

        agentExecLogService.audit("TEST_PLAN_CREATE", plan.getId(), JSON.toJSONString(request));
        return toDto(plan, associated);
    }

    public void associate(AgentTestPlanAssociateRequest request) {
        associate(request, requireSessionUser());
        agentExecLogService.audit("TEST_PLAN_ASSOCIATE", request.getTestPlanId(), JSON.toJSONString(request));
    }

    private void associate(AgentTestPlanAssociateRequest request, SessionUser user) {
        TestPlanFunctionalCaseAssociateRequest associateRequest = new TestPlanFunctionalCaseAssociateRequest();
        associateRequest.setProjectId(request.getProjectId());
        associateRequest.setTestPlanId(request.getTestPlanId());
        associateRequest.setSelectIds(request.getCaseIds());
        associateRequest.setSelectAll(false);
        associateRequest.setCollectionId(request.getCollectionId());
        testPlanFunctionalCaseService.associateCases(associateRequest, user);
    }

    public AgentTestPlanDTO get(String id) {
        TestPlan plan = testPlanMapper.selectByPrimaryKey(id);
        if (plan == null) {
            throw new MSException("测试计划不存在: " + id);
        }
        return toDto(plan, null);
    }

    private AgentTestPlanDTO toDto(TestPlan plan, Integer associated) {
        AgentTestPlanDTO dto = new AgentTestPlanDTO();
        dto.setId(plan.getId());
        dto.setName(plan.getName());
        dto.setProjectId(plan.getProjectId());
        dto.setStatus(plan.getStatus());
        dto.setNum(plan.getNum());
        dto.setAssociatedCaseCount(associated);
        return dto;
    }

    private String requireUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new MSException("无法解析 Agent Token 对应用户");
        }
        return userId;
    }

    private SessionUser requireSessionUser() {
        SessionUser user = SessionUtils.getUser();
        if (user == null) {
            throw new MSException("无法解析 Agent Token 会话用户");
        }
        return user;
    }
}
