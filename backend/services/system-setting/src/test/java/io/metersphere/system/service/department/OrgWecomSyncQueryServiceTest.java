package io.metersphere.system.service.department;

import io.metersphere.system.domain.OrgSyncLog;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.dto.department.OrgWecomSyncLogPageRequest;
import io.metersphere.system.dto.department.OrgWecomSyncStatusDTO;
import io.metersphere.system.mapper.ExtOrgSyncLogMapper;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgWecomSyncQueryServiceTest {

    @InjectMocks
    private OrgWecomSyncQueryService orgWecomSyncQueryService;
    @Mock
    private OrgWecomSyncAccessService orgWecomSyncAccessService;
    @Mock
    private ExtOrgSyncLogMapper extOrgSyncLogMapper;
    @Mock
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;

    @Test
    void getStatus_returnsLatestLogAndLastSyncTime() {
        doNothing().when(orgWecomSyncAccessService).validateReadable("org-1");

        OrgWecomSyncConfig config = new OrgWecomSyncConfig();
        config.setLastSyncTime(1000L);
        when(orgWecomSyncConfigMapper.selectByExample(any())).thenReturn(List.of(config));

        OrgSyncLog log = new OrgSyncLog();
        log.setId("log-1");
        log.setSyncMode("MANUAL");
        log.setSyncStatus("SUCCESS");
        log.setDeptSuccess(2);
        log.setUserSuccess(3);
        log.setCreateTime(2000L);
        when(extOrgSyncLogMapper.selectLatestByOrganizationId("org-1")).thenReturn(log);

        OrgWecomSyncStatusDTO status = orgWecomSyncQueryService.getStatus("org-1");

        Assertions.assertEquals(1000L, status.getLastSyncTime());
        Assertions.assertEquals("log-1", status.getSyncLogId());
        Assertions.assertEquals("SUCCESS", status.getSyncStatus());
        Assertions.assertEquals(2, status.getDeptSuccess());
        Assertions.assertEquals(3, status.getUserSuccess());
    }

    @Test
    void pageLogs_returnsFilteredLogs() {
        doNothing().when(orgWecomSyncAccessService).validateReadable("org-1");
        OrgWecomSyncLogPageRequest request = new OrgWecomSyncLogPageRequest();
        request.setOrganizationId("org-1");
        request.setSyncStatus("FAILED");
        when(extOrgSyncLogMapper.listByOrganizationId("org-1", "FAILED")).thenReturn(List.of(new OrgSyncLog()));

        List<OrgSyncLog> logs = orgWecomSyncQueryService.pageLogs(request);

        Assertions.assertEquals(1, logs.size());
    }
}
