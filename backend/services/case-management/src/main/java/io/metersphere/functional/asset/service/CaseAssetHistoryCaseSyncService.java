package io.metersphere.functional.asset.service;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Transaction boundary for one historical case, so one bad record cannot roll back a whole project. */
@Service
public class CaseAssetHistoryCaseSyncService {
    @Lazy
    @Resource
    private CaseAssetService caseAssetService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public String sync(String projectId, String organizationId, String operator,
                       String hubModuleId, String sourceCaseId) {
        return caseAssetService.syncHistoricalCase(projectId, organizationId, operator, hubModuleId, sourceCaseId);
    }
}
