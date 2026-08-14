package io.metersphere.bug.service;

import io.metersphere.bug.dto.request.BugTransitionBatchExecuteRequest;
import io.metersphere.bug.dto.response.BugTransitionDTO;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BugWorkflowBatchService {
    @Resource private BugWorkflowRuntimeService runtimeService;

    public Map<String, Object> execute(BugTransitionBatchExecuteRequest request) {
        List<Map<String, Object>> successes = new ArrayList<>();
        List<Map<String, Object>> failures = new ArrayList<>();
        for (BugTransitionBatchExecuteRequest.Item item : request.getItems()) {
            try {
                BugTransitionDTO result = runtimeService.transition(item.getBugId(), item);
                successes.add(Map.of("bugId", item.getBugId(), "runtime", result));
            } catch (Exception e) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("bugId", item.getBugId());
                failure.put("message", StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName()));
                failures.add(failure);
            }
        }
        return Map.of("successCount", successes.size(), "failureCount", failures.size(),
                "successes", successes, "failures", failures);
    }
}
