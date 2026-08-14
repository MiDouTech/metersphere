package io.metersphere.system.service;

import io.metersphere.system.mapper.PermissionControlMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PermissionMigrationAuditService {

    @Resource
    private PermissionControlMapper permissionControlMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String migrationVersion,
                              String sourceRoleId,
                              String userId,
                              String failureStage,
                              Exception exception) {
        permissionControlMapper.insertMigrationFailure(
                migrationVersion,
                sourceRoleId,
                userId,
                failureStage,
                StringUtils.defaultString(exception.getMessage(), exception.getClass().getSimpleName()),
                System.currentTimeMillis());
    }
}
