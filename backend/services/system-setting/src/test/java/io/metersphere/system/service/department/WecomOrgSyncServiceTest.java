package io.metersphere.system.service.department;

import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.dto.department.SyncResult;
import io.metersphere.system.dto.wecom.WecomDepartmentDTO;
import io.metersphere.system.service.wecom.WecomContactClient;
import io.metersphere.system.service.wecom.WecomContactConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WecomOrgSyncServiceTest {

    @InjectMocks
    private WecomOrgSyncService wecomOrgSyncService;
    @Mock
    private WecomContactConfigProvider wecomContactConfigProvider;
    @Mock
    private WecomContactClient wecomContactClient;
    @Mock
    private DepartmentSyncHandler departmentSyncHandler;
    @Mock
    private UserSyncHandler userSyncHandler;

    @Test
    void syncAll_aggregatesDepartmentAndUserResults() {
        OrgWecomSyncConfig config = new OrgWecomSyncConfig();
        config.setCorpId("corp");
        config.setContactSecret("secret");
        when(wecomContactConfigProvider.getByOrganizationId("org-1")).thenReturn(config);
        when(wecomContactClient.executeWithToken(eq("corp"), eq("secret"), any())).thenAnswer(invocation -> {
            Function<String, List<WecomDepartmentDTO>> action = invocation.getArgument(2);
            return action.apply("token");
        });
        when(wecomContactClient.listDepartments("token")).thenReturn(List.of(new WecomDepartmentDTO()));

        SyncPartResult deptResult = new SyncPartResult();
        deptResult.setTotal(2);
        deptResult.setSuccess(2);
        deptResult.setCreated(1);
        deptResult.setUpdated(1);
        when(departmentSyncHandler.sync(eq("org-1"), eq("admin"), any())).thenReturn(deptResult);

        SyncPartResult userResult = new SyncPartResult();
        userResult.setTotal(3);
        userResult.setSuccess(2);
        userResult.setFailed(1);
        userResult.setCreated(1);
        userResult.setUpdated(1);
        userResult.setDisabled(1);
        userResult.appendError("用户[a]同步失败; ");
        when(userSyncHandler.sync("org-1", "admin", "corp", "secret")).thenReturn(userResult);

        SyncResult result = wecomOrgSyncService.syncAll("org-1", "admin");

        Assertions.assertEquals(OrgSyncConstants.SYNC_STATUS_PARTIAL, result.getSyncStatus());
        Assertions.assertEquals(2, result.getDeptTotal());
        Assertions.assertEquals(3, result.getUserTotal());
        Assertions.assertEquals(1, result.getUserFailed());
        Assertions.assertEquals(1, result.getDeptCreated());
        Assertions.assertEquals(1, result.getUserDisabled());
        Assertions.assertTrue(result.getErrorMessage().contains("用户[a]同步失败"));
    }
}
