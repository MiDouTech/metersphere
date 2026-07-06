package io.metersphere.system.service.department;

import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.dto.department.OrgWecomSyncConfigDTO;
import io.metersphere.system.dto.department.OrgWecomSyncConfigSaveRequest;
import io.metersphere.system.dto.department.OrgWecomSyncConfigTestRequest;
import io.metersphere.system.dto.department.OrgWecomSyncConfigTestResponse;
import io.metersphere.system.log.service.OperationLogService;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import io.metersphere.system.service.wecom.WecomApiException;
import io.metersphere.system.service.wecom.WecomContactClient;
import io.metersphere.system.uid.IDGenerator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgWecomSyncConfigServiceTest {

    @InjectMocks
    private OrgWecomSyncConfigService orgWecomSyncConfigService;
    @Mock
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;
    @Mock
    private OrgWecomSyncAccessService orgWecomSyncAccessService;
    @Mock
    private WecomOrgSyncScheduleService wecomOrgSyncScheduleService;
    @Mock
    private WecomContactClient wecomContactClient;
    @Mock
    private OperationLogService operationLogService;

    @Test
    void save_firstTime_insertsConfigAndRefreshesSchedule() {
        try (MockedStatic<IDGenerator> idGenerator = Mockito.mockStatic(IDGenerator.class)) {
            idGenerator.when(IDGenerator::nextStr).thenReturn("cfg-1");
            doNothing().when(orgWecomSyncAccessService).validateWritable("org-1");
            when(orgWecomSyncConfigMapper.selectByExample(any())).thenReturn(new ArrayList<>(), List.of(buildConfig()));

            OrgWecomSyncConfigSaveRequest request = new OrgWecomSyncConfigSaveRequest();
            request.setOrganizationId("org-1");
            request.setCorpId("corp");
            request.setContactSecret("secret");
            request.setScheduleEnabled(true);
            request.setScheduleCron("0 0 2 * * ?");

            orgWecomSyncConfigService.save(request, "admin");

            ArgumentCaptor<OrgWecomSyncConfig> captor = ArgumentCaptor.forClass(OrgWecomSyncConfig.class);
            verify(orgWecomSyncConfigMapper).insert(captor.capture());
            Assertions.assertEquals("corp", captor.getValue().getCorpId());
            verify(wecomOrgSyncScheduleService).refreshSchedule(any(OrgWecomSyncConfig.class));
            verify(operationLogService).add(any());
        }
    }

    @Test
    void save_updateWithMaskedSecret_keepsOriginalSecret() {
        doNothing().when(orgWecomSyncAccessService).validateWritable("org-1");
        OrgWecomSyncConfig existing = buildConfig();
        when(orgWecomSyncConfigMapper.selectByExample(any())).thenReturn(List.of(existing));

        OrgWecomSyncConfigSaveRequest request = new OrgWecomSyncConfigSaveRequest();
        request.setOrganizationId("org-1");
        request.setCorpId("corp-new");
        request.setContactSecret(OrgWecomSyncSecretUtils.maskContactSecret("secret"));
        request.setScheduleEnabled(true);
        request.setScheduleCron("0 0 3 * * ?");

        orgWecomSyncConfigService.save(request, "admin");

        ArgumentCaptor<OrgWecomSyncConfig> captor = ArgumentCaptor.forClass(OrgWecomSyncConfig.class);
        verify(orgWecomSyncConfigMapper).updateByPrimaryKeySelective(captor.capture());
        Assertions.assertNull(captor.getValue().getContactSecret());
        Assertions.assertEquals("corp-new", captor.getValue().getCorpId());
    }

    @Test
    void get_masksContactSecret() {
        doNothing().when(orgWecomSyncAccessService).validateReadable("org-1");
        when(orgWecomSyncConfigMapper.selectByExample(any())).thenReturn(List.of(buildConfig()));

        OrgWecomSyncConfigDTO dto = orgWecomSyncConfigService.get("org-1");

        Assertions.assertTrue(dto.getConfigured());
        Assertions.assertEquals(OrgWecomSyncSecretUtils.maskContactSecret("secret"), dto.getContactSecret());
    }

    @Test
    void get_notConfigured_returnsDefaults() {
        doNothing().when(orgWecomSyncAccessService).validateReadable("org-1");
        when(orgWecomSyncConfigMapper.selectByExample(any())).thenReturn(new ArrayList<>());

        OrgWecomSyncConfigDTO dto = orgWecomSyncConfigService.get("org-1");

        Assertions.assertFalse(dto.getConfigured());
        Assertions.assertEquals(3, dto.getRetryTimes());
        Assertions.assertFalse(dto.getScheduleEnabled());
    }

    @Test
    void test_success_returnsDeptCount() {
        doNothing().when(orgWecomSyncAccessService).validateWritable("org-1");
        when(orgWecomSyncConfigMapper.selectByExample(any())).thenReturn(List.of(buildConfig()));
        doAnswer(invocation -> {
            Function<String, Integer> action = invocation.getArgument(2);
            return action.apply("token");
        }).when(wecomContactClient).executeWithToken(eq("corp"), eq("secret"), any());
        when(wecomContactClient.listDepartments("token")).thenReturn(List.of(new io.metersphere.system.dto.wecom.WecomDepartmentDTO(), new io.metersphere.system.dto.wecom.WecomDepartmentDTO()));

        OrgWecomSyncConfigTestRequest request = new OrgWecomSyncConfigTestRequest();
        request.setOrganizationId("org-1");
        request.setCorpId("corp");
        request.setContactSecret("secret");

        OrgWecomSyncConfigTestResponse response = orgWecomSyncConfigService.test(request, "admin");

        Assertions.assertTrue(response.isSuccess());
        Assertions.assertEquals(2, response.getDeptCount());
        Assertions.assertEquals("连接成功", response.getMessage());
    }

    @Test
    void test_failure_returnsErrcodeMessage() {
        doNothing().when(orgWecomSyncAccessService).validateWritable("org-1");
        when(orgWecomSyncConfigMapper.selectByExample(any())).thenReturn(List.of(buildConfig()));
        doAnswer(invocation -> {
            throw new WecomApiException(40001, "invalid credential");
        }).when(wecomContactClient).executeWithToken(eq("corp"), eq("secret"), any());

        OrgWecomSyncConfigTestRequest request = new OrgWecomSyncConfigTestRequest();
        request.setOrganizationId("org-1");
        request.setCorpId("corp");
        request.setContactSecret("secret");

        OrgWecomSyncConfigTestResponse response = orgWecomSyncConfigService.test(request, "admin");

        Assertions.assertFalse(response.isSuccess());
        Assertions.assertEquals(0, response.getDeptCount());
        Assertions.assertTrue(response.getMessage().contains("40001"));
    }

    private OrgWecomSyncConfig buildConfig() {
        OrgWecomSyncConfig config = new OrgWecomSyncConfig();
        config.setId("cfg-1");
        config.setOrganizationId("org-1");
        config.setCorpId("corp");
        config.setContactSecret("secret");
        config.setScheduleEnabled(1);
        config.setScheduleCron("0 0 2 * * ?");
        config.setRetryTimes(3);
        return config;
    }
}
