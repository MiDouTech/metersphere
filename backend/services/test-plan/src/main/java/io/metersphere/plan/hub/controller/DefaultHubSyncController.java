package io.metersphere.plan.hub.controller;

import io.metersphere.functional.hub.dto.DefaultHubJobResponse;
import io.metersphere.functional.hub.dto.DefaultHubSyncRequest;
import io.metersphere.plan.hub.service.DefaultHubSyncAccessService;
import io.metersphere.plan.hub.service.DefaultHubSyncJobService;
import io.metersphere.system.utils.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/default-hub")
@Tag(name = "默认项目枢纽")
public class DefaultHubSyncController {

    @Resource
    private DefaultHubSyncJobService defaultHubSyncJobService;
    @Resource
    private DefaultHubSyncAccessService defaultHubSyncAccessService;

    @GetMapping("/default-project-id")
    @Operation(summary = "获取系统默认项目ID")
    public String getDefaultProjectId() {
        return defaultHubSyncJobService.getDefaultProjectId();
    }

    @PostMapping("/sync")
    @Operation(summary = "手动触发枢纽同步对账")
    public DefaultHubJobResponse sync(@Validated @RequestBody(required = false) DefaultHubSyncRequest request) {
        String userId = SessionUtils.getUserId();
        String projectId = request == null ? null : request.getProjectId();
        defaultHubSyncAccessService.assertManualSyncPermission(userId, projectId);
        return defaultHubSyncJobService.startManualSync(projectId, userId);
    }

    @GetMapping("/sync/{jobId}")
    @Operation(summary = "查询枢纽同步/导入任务进度")
    public DefaultHubJobResponse getSyncJob(@PathVariable String jobId) {
        return defaultHubSyncJobService.getJob(jobId);
    }
}
