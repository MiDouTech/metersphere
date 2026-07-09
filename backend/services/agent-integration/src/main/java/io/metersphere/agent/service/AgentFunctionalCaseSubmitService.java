package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseSubmitRequest;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.functional.domain.FunctionalCase;
import io.metersphere.functional.domain.FunctionalCaseBlob;
import io.metersphere.functional.dto.FunctionalCaseStepDTO;
import io.metersphere.functional.mapper.FunctionalCaseBlobMapper;
import io.metersphere.functional.mapper.FunctionalCaseMapper;
import io.metersphere.plan.dto.request.TestPlanCaseRunRequest;
import io.metersphere.plan.service.TestPlanFunctionalCaseService;
import io.metersphere.sdk.constants.HttpMethodConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.dto.LogInsertModule;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class AgentFunctionalCaseSubmitService {
    @Resource
    private TestPlanFunctionalCaseService testPlanFunctionalCaseService;
    @Resource
    private AgentCaseSchemaMapper agentCaseSchemaMapper;
    @Resource
    private AgentExecLogService agentExecLogService;
    @Resource
    private AgentAttachmentService agentAttachmentService;
    @Resource
    private FunctionalCaseMapper functionalCaseMapper;
    @Resource
    private SqlSessionFactory sqlSessionFactory;

    @Transactional(rollbackFor = Exception.class)
    public void submit(AgentCaseSubmitRequest request) {
        validateSubmitRequest(request);
        if (StringUtils.isNotBlank(request.getTestPlanCaseId())) {
            submitInPlan(request);
        } else {
            submitOutOfPlan(request);
        }
    }

    private void validateSubmitRequest(AgentCaseSubmitRequest request) {
        boolean hasPlanId = StringUtils.isNotBlank(request.getTestPlanId());
        boolean hasPlanCaseId = StringUtils.isNotBlank(request.getTestPlanCaseId());
        if (hasPlanId != hasPlanCaseId) {
            throw new MSException("testPlanId 与 testPlanCaseId 必须同时提供或同时为空");
        }
    }

    private void submitInPlan(AgentCaseSubmitRequest request) {
        TestPlanCaseRunRequest runRequest = new TestPlanCaseRunRequest();
        runRequest.setProjectId(request.getProjectId());
        runRequest.setId(request.getTestPlanCaseId());
        runRequest.setCaseId(request.getCaseId());
        runRequest.setTestPlanId(request.getTestPlanId());
        runRequest.setLastExecResult(request.getLastExecResult());
        runRequest.setStepsExecResult(agentCaseSchemaMapper.toStepsExecResultJson(request.getSteps()));
        runRequest.setContent(formatContent(request.getExecutedBy(), request.getContent()));
        if (CollectionUtils.isNotEmpty(request.getAttachmentIds())) {
            runRequest.setPlanCommentFileIds(resolveFileIds(request.getAttachmentIds()));
        }
        testPlanFunctionalCaseService.run(runRequest,
                new LogInsertModule(SessionUtils.getUserId(), "/api/agent/v1/functional/submit", HttpMethodConstants.POST.name()));
        agentAttachmentService.linkToPlanSubmit(new AgentAttachmentService.AgentCaseSubmitRequestHolder(
                request.getProjectId(), request.getCaseId(), request.getTestPlanId(),
                request.getTestPlanCaseId(), request.getAttachmentIds()));
    }

    private void submitOutOfPlan(AgentCaseSubmitRequest request) {
        FunctionalCase functionalCase = functionalCaseMapper.selectByPrimaryKey(request.getCaseId());
        if (functionalCase == null) {
            throw new MSException("用例不存在: " + request.getCaseId());
        }
        String stepsJson = agentCaseSchemaMapper.toStepsExecResultJson(request.getSteps());
        updateFunctionalCaseStatus(request.getCaseId(), request.getLastExecResult(), stepsJson);
        String execLogId = agentExecLogService.log(request, stepsJson);
        agentAttachmentService.linkToExecLog(execLogId, request.getAttachmentIds());
    }

    private List<String> resolveFileIds(List<String> attachmentIds) {
        return attachmentIds.stream()
                .map(agentAttachmentService::get)
                .filter(item -> item != null && StringUtils.isNotBlank(item.getFileId()))
                .map(item -> item.getFileId())
                .toList();
    }

    private void updateFunctionalCaseStatus(String caseId, String lastExecResult, String stepsExecResult) {
        SqlSession sqlSession = sqlSessionFactory.openSession(ExecutorType.BATCH);
        FunctionalCaseMapper caseMapper = sqlSession.getMapper(FunctionalCaseMapper.class);
        FunctionalCaseBlobMapper blobMapper = sqlSession.getMapper(FunctionalCaseBlobMapper.class);
        String steps = normalizeSteps(stepsExecResult);
        FunctionalCase functionalCase = new FunctionalCase();
        functionalCase.setId(caseId);
        functionalCase.setLastExecuteResult(lastExecResult);
        caseMapper.updateByPrimaryKeySelective(functionalCase);
        if (StringUtils.isNotBlank(steps)) {
            FunctionalCaseBlob blob = new FunctionalCaseBlob();
            blob.setId(caseId);
            blob.setSteps(steps.getBytes(StandardCharsets.UTF_8));
            blobMapper.updateByPrimaryKeySelective(blob);
        }
        sqlSession.flushStatements();
        SqlSessionUtils.closeSqlSession(sqlSession, sqlSessionFactory);
    }

    private String normalizeSteps(String stepsExecResult) {
        if (StringUtils.isBlank(stepsExecResult)) {
            return null;
        }
        List<FunctionalCaseStepDTO> steps = JSON.parseArray(stepsExecResult, FunctionalCaseStepDTO.class);
        if (CollectionUtils.isEmpty(steps)) {
            return null;
        }
        steps.forEach(step -> {
            if (StringUtils.isBlank(step.getId())) {
                step.setId(UUID.randomUUID().toString());
            }
        });
        return JSON.toJSONString(steps);
    }

    private String formatContent(String executedBy, String content) {
        if (StringUtils.isNotBlank(executedBy)) {
            return "[" + executedBy + "] " + StringUtils.defaultString(content);
        }
        return content;
    }
}
