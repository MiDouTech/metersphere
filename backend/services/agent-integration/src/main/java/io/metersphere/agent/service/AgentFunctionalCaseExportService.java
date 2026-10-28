package io.metersphere.agent.service;

import io.metersphere.agent.dto.AgentCaseDTO;
import io.metersphere.agent.mapper.AgentCaseSchemaMapper;
import io.metersphere.functional.dto.FunctionalCaseDetailDTO;
import io.metersphere.functional.service.FunctionalCaseService;
import io.metersphere.system.utils.SessionUtils;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentFunctionalCaseExportService {
    @Resource
    private io.metersphere.functional.service.FunctionalExecutionViewService executionViews;
    @Resource
    private io.metersphere.plan.service.TestPlanFunctionalCaseService planCases;
    @Resource
    private FunctionalCaseService functionalCaseService;
    @Resource
    private AgentCaseSchemaMapper agentCaseSchemaMapper;

    public void enrichWithSteps(List<AgentCaseDTO> cases, List<String> warnings) {
        if (CollectionUtils.isEmpty(cases)) {
            return;
        }
        var failures = new java.util.HashMap<String, String>();
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        var decodedBytes = new java.util.concurrent.atomic.AtomicLong();
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        for (int start = 0; start < cases.size(); start += 200) {
            List<AgentCaseDTO> chunk = cases.subList(start, Math.min(start + 200, cases.size()));
            String projectId = chunk.getFirst().getProjectId();
            if (projectId == null || chunk.stream().anyMatch(item -> !projectId.equals(item.getProjectId())))
                throw new io.metersphere.sdk.exception.MSException("单批读取必须属于同一个已授权项目");
            var details = readChunk(chunk, projectId, failures, warnings, reads, decodedBytes, deadline);
            for (AgentCaseDTO item : chunk) {
                if (failures.containsKey(item.getCaseId())) {
                    item.setContentError(failures.get(item.getCaseId()));
                    item.setSteps(null);
                    item.setExecutionRef(null);
                    item.setExecutionFacts(null);
                    continue;
                }
                FunctionalCaseDetailDTO detail = details.get(item.getCaseId());
                if (detail == null || (item.getProjectId() != null && !item.getProjectId().equals(detail.getProjectId())))
                    throw new io.metersphere.sdk.exception.MSException("用例已变更或无权访问，请重新检索");
                agentCaseSchemaMapper.enrichDetail(item, detail, warnings);
                if (item.getTestPlanCaseId() != null) {
                    var effective = planCases.getFunctionalCaseDetail(item.getTestPlanCaseId(), SessionUtils.getUserId());
                    item.setExecutionRef(effective.getExecutionRef());
                    item.setExecutionFacts(effective.getExecutionFacts());
                } else {
                    executionViews.enrich(detail);
                    item.setExecutionRef(detail.getExecutionRef());
                    item.setExecutionFacts(detail.getExecutionFacts());
                }
            }
        }
    }
    // Keep the normal batch path. Only a known content-format failure permits bounded isolation.
    private java.util.Map<String, FunctionalCaseDetailDTO> readChunk(List<AgentCaseDTO> chunk, String projectId,
            java.util.Map<String, String> failures, List<String> warnings,
            java.util.concurrent.atomic.AtomicInteger reads, java.util.concurrent.atomic.AtomicLong decodedBytes, long deadline) {
        if (reads.get() >= 64 || System.nanoTime() > deadline) {
            markFailed(chunk, "CASE_CONTENT_READ_LIMIT", failures, warnings);
            return java.util.Map.of();
        }
        reads.incrementAndGet();
        try {
            var result = functionalCaseService.getAgentCaseDetailsForSearch(chunk.stream().map(AgentCaseDTO::getCaseId).toList(), projectId);
            long size = 0;
            for (var detail : result.values()) {
                for (String text : new String[]{detail.getSteps(), detail.getTextDescription(), detail.getExpectedResult(),
                        detail.getDescription(), detail.getPrerequisite()}) size += text == null ? 0 : 2L * text.length();
            }
            if (decodedBytes.addAndGet(size) > 32L * 1024 * 1024) {
                markFailed(chunk, "CASE_CONTENT_READ_LIMIT", failures, warnings);
                return java.util.Map.of();
            }
            return result;
        } catch (RuntimeException failure) {
            boolean bodyFailure = false;
            Throwable cause = failure;
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof io.metersphere.sdk.util.FunctionalCaseBlobCodec.BodyFormatException) {
                    bodyFailure = true;
                    break;
                }
            }
            if (!bodyFailure) throw failure; // Never swallow permission, disappearance, or database errors.
            if (chunk.size() == 1) {
                markFailed(chunk, "CASE_BODY_INVALID", failures, warnings);
                return java.util.Map.of();
            }
            int middle = chunk.size() / 2;
            var result = new java.util.LinkedHashMap<String, FunctionalCaseDetailDTO>();
            result.putAll(readChunk(chunk.subList(0, middle), projectId, failures, warnings, reads, decodedBytes, deadline));
            result.putAll(readChunk(chunk.subList(middle, chunk.size()), projectId, failures, warnings, reads, decodedBytes, deadline));
            return result;
        }
    }

    private static void markFailed(List<AgentCaseDTO> cases, String code, java.util.Map<String, String> failures,
                                   List<String> warnings) {
        for (var item : cases) {
            failures.put(item.getCaseId(), code);
            warnings.add(code + ":" + item.getCaseId());
        }
    }
}
