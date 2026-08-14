package io.metersphere.functional.asset.service;

import io.metersphere.system.service.CleanupProjectResourceService;
import io.metersphere.system.service.CreateProjectResourceService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class CaseAssetProjectResourceService implements CreateProjectResourceService, CleanupProjectResourceService {
    @Resource private CaseAssetService caseAssetService;

    @Override
    public void createResources(String projectId) {
        caseAssetService.upsertForProject(projectId);
    }

    @Override
    public void deleteResources(String projectId) {
        caseAssetService.unlinkProject(projectId);
    }
}
