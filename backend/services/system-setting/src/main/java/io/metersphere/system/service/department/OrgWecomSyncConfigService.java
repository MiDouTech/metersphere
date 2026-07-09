package io.metersphere.system.service.department;

import io.metersphere.sdk.constants.HttpMethodConstants;
import io.metersphere.sdk.constants.OperationLogConstants;
import io.metersphere.sdk.exception.MSException;
import io.metersphere.sdk.util.Translator;
import io.metersphere.sdk.util.JSON;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.domain.OrgWecomSyncConfigExample;
import io.metersphere.system.dto.builder.LogDTOBuilder;
import io.metersphere.system.dto.department.OrgWecomSyncConfigDTO;
import io.metersphere.system.dto.department.OrgWecomSyncConfigSaveRequest;
import io.metersphere.system.dto.department.OrgWecomSyncConfigTestRequest;
import io.metersphere.system.dto.department.OrgWecomSyncConfigTestResponse;
import io.metersphere.system.log.constants.OperationLogModule;
import io.metersphere.system.log.constants.OperationLogType;
import io.metersphere.system.log.dto.LogDTO;
import io.metersphere.system.log.service.OperationLogService;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import io.metersphere.system.service.wecom.WecomApiException;
import io.metersphere.system.service.wecom.WecomContactClient;
import io.metersphere.system.uid.IDGenerator;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(rollbackFor = Exception.class)
public class OrgWecomSyncConfigService {

    @Resource
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;
    @Resource
    private OrgWecomSyncAccessService orgWecomSyncAccessService;
    @Resource
    private WecomOrgSyncScheduleService wecomOrgSyncScheduleService;
    @Resource
    private WecomContactClient wecomContactClient;
    @Resource
    private OperationLogService operationLogService;

    public OrgWecomSyncConfigDTO get(String organizationId) {
        orgWecomSyncAccessService.validateReadable(organizationId);
        OrgWecomSyncConfig existing = getByOrganizationId(organizationId);
        if (existing == null) {
            OrgWecomSyncConfigDTO dto = new OrgWecomSyncConfigDTO();
            dto.setOrganizationId(organizationId);
            dto.setScheduleEnabled(false);
            dto.setRetryTimes(3);
            dto.setConfigured(false);
            return dto;
        }
        return toDto(existing);
    }

    public OrgWecomSyncConfigTestResponse test(OrgWecomSyncConfigTestRequest request, String operatorId) {
        orgWecomSyncAccessService.validateWritable(request.getOrganizationId());
        OrgWecomSyncConfig existing = getByOrganizationId(request.getOrganizationId());
        String corpId = StringUtils.defaultIfBlank(request.getCorpId(), existing == null ? null : existing.getCorpId());
        String contactSecret = resolveContactSecret(request.getContactSecret(), existing);
        if (StringUtils.isAnyBlank(corpId, contactSecret)) {
            throw new MSException(Translator.get("org.wecom.sync.corp_id_and_secret.required"));
        }
        OrgWecomSyncConfigTestResponse response = new OrgWecomSyncConfigTestResponse();
        try {
            int deptCount = wecomContactClient.executeWithToken(corpId, contactSecret,
                    token -> wecomContactClient.listDepartments(token).size());
            response.setSuccess(true);
            response.setDeptCount(deptCount);
            response.setMessage("连接成功");
            addOperationLog(request.getOrganizationId(), existing == null ? request.getOrganizationId() : existing.getId(),
                    operatorId, OperationLogType.DEBUG.name(), "/org-wecom/config/test",
                    "测试企微通讯录连接成功，部门数: " + deptCount, null, JSON.toJSONBytes(response));
        } catch (WecomApiException e) {
            response.setSuccess(false);
            response.setDeptCount(0);
            response.setMessage("errcode: " + e.getErrcode() + ", " + e.getMessage());
            addOperationLog(request.getOrganizationId(), existing == null ? request.getOrganizationId() : existing.getId(),
                    operatorId, OperationLogType.DEBUG.name(), "/org-wecom/config/test",
                    "测试企微通讯录连接失败: " + response.getMessage(), null, JSON.toJSONBytes(response));
        }
        return response;
    }

    public void save(OrgWecomSyncConfigSaveRequest request, String operatorId) {
        orgWecomSyncAccessService.validateWritable(request.getOrganizationId());
        validateSchedule(request);
        OrgWecomSyncConfig existing = getByOrganizationId(request.getOrganizationId());
        if (existing == null && shouldSkipSecretUpdate(request.getContactSecret())) {
            throw new MSException(Translator.get("org.wecom.sync.contact_secret.required"));
        }
        long now = System.currentTimeMillis();
        OrgWecomSyncConfig config;
        if (existing == null) {
            config = new OrgWecomSyncConfig();
            config.setId(IDGenerator.nextStr());
            config.setOrganizationId(request.getOrganizationId());
            config.setCorpId(request.getCorpId());
            config.setContactSecret(request.getContactSecret());
            config.setAgentId(request.getAgentId());
            config.setScheduleEnabled(Boolean.TRUE.equals(request.getScheduleEnabled()) ? 1 : 0);
            config.setScheduleCron(request.getScheduleCron());
            config.setRetryTimes(defaultRetryTimes(request.getRetryTimes()));
            config.setCreateTime(now);
            config.setUpdateTime(now);
            config.setCreateUser(operatorId);
            config.setUpdateUser(operatorId);
            orgWecomSyncConfigMapper.insert(config);
            addOperationLog(request.getOrganizationId(), config.getId(), operatorId,
                    OperationLogType.ADD.name(), "/org-wecom/config/save", "保存企微同步配置", null,
                    JSON.toJSONBytes(maskConfigForLog(config)));
        } else {
            OrgWecomSyncConfig update = new OrgWecomSyncConfig();
            update.setId(existing.getId());
            update.setCorpId(request.getCorpId());
            if (!shouldSkipSecretUpdate(request.getContactSecret())) {
                update.setContactSecret(request.getContactSecret());
            }
            update.setAgentId(request.getAgentId());
            update.setScheduleEnabled(Boolean.TRUE.equals(request.getScheduleEnabled()) ? 1 : 0);
            update.setScheduleCron(request.getScheduleCron());
            update.setRetryTimes(defaultRetryTimes(request.getRetryTimes()));
            update.setUpdateTime(now);
            update.setUpdateUser(operatorId);
            orgWecomSyncConfigMapper.updateByPrimaryKeySelective(update);
            config = loadConfig(request.getOrganizationId());
            addOperationLog(request.getOrganizationId(), config.getId(), operatorId,
                    OperationLogType.UPDATE.name(), "/org-wecom/config/save", "更新企微同步配置",
                    JSON.toJSONBytes(maskConfigForLog(existing)), JSON.toJSONBytes(maskConfigForLog(config)));
        }
        wecomOrgSyncScheduleService.refreshSchedule(loadConfig(request.getOrganizationId()));
    }

    private OrgWecomSyncConfig loadConfig(String organizationId) {
        OrgWecomSyncConfig config = getByOrganizationId(organizationId);
        if (config == null) {
            throw new MSException(Translator.getWithArgs("org.wecom.sync.config.not_found", organizationId));
        }
        return config;
    }

    private OrgWecomSyncConfig getByOrganizationId(String organizationId) {
        OrgWecomSyncConfigExample example = new OrgWecomSyncConfigExample();
        example.createCriteria().andOrganizationIdEqualTo(organizationId);
        List<OrgWecomSyncConfig> configs = orgWecomSyncConfigMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(configs)) {
            return null;
        }
        return configs.getFirst();
    }

    private OrgWecomSyncConfigDTO toDto(OrgWecomSyncConfig config) {
        OrgWecomSyncConfigDTO dto = new OrgWecomSyncConfigDTO();
        dto.setOrganizationId(config.getOrganizationId());
        dto.setCorpId(config.getCorpId());
        dto.setContactSecret(OrgWecomSyncSecretUtils.maskContactSecret(config.getContactSecret()));
        dto.setAgentId(config.getAgentId());
        dto.setScheduleEnabled(config.getScheduleEnabled() != null && config.getScheduleEnabled() == 1);
        dto.setScheduleCron(config.getScheduleCron());
        dto.setRetryTimes(config.getRetryTimes());
        dto.setLastSyncTime(config.getLastSyncTime());
        dto.setConfigured(true);
        return dto;
    }

    private OrgWecomSyncConfig maskConfigForLog(OrgWecomSyncConfig config) {
        OrgWecomSyncConfig masked = new OrgWecomSyncConfig();
        masked.setId(config.getId());
        masked.setOrganizationId(config.getOrganizationId());
        masked.setCorpId(config.getCorpId());
        masked.setContactSecret(OrgWecomSyncSecretUtils.maskContactSecret(config.getContactSecret()));
        masked.setAgentId(config.getAgentId());
        masked.setScheduleEnabled(config.getScheduleEnabled());
        masked.setScheduleCron(config.getScheduleCron());
        masked.setRetryTimes(config.getRetryTimes());
        masked.setLastSyncTime(config.getLastSyncTime());
        return masked;
    }

    private String resolveContactSecret(String requestSecret, OrgWecomSyncConfig existing) {
        if (shouldSkipSecretUpdate(requestSecret)) {
            if (existing == null || StringUtils.isBlank(existing.getContactSecret())) {
                throw new MSException(Translator.get("org.wecom.sync.contact_secret.required"));
            }
            return existing.getContactSecret();
        }
        return requestSecret;
    }

    private boolean shouldSkipSecretUpdate(String contactSecret) {
        return StringUtils.isBlank(contactSecret) || OrgWecomSyncSecretUtils.isMaskedSecret(contactSecret);
    }

    private void validateSchedule(OrgWecomSyncConfigSaveRequest request) {
        if (Boolean.TRUE.equals(request.getScheduleEnabled()) && StringUtils.isBlank(request.getScheduleCron())) {
            throw new MSException(Translator.get("org.wecom.sync.schedule_cron.required"));
        }
    }

    private int defaultRetryTimes(Integer retryTimes) {
        return retryTimes == null || retryTimes < 0 ? 3 : retryTimes;
    }

    private void addOperationLog(String organizationId, String sourceId, String operatorId, String type,
                                 String path, String content, byte[] originalValue, byte[] modifiedValue) {
        LogDTO logDTO = LogDTOBuilder.builder()
                .projectId(OperationLogConstants.ORGANIZATION)
                .organizationId(organizationId)
                .sourceId(sourceId)
                .createUser(operatorId)
                .type(type)
                .module(OperationLogModule.SETTING_ORGANIZATION_MEMBER)
                .method(HttpMethodConstants.POST.name())
                .path(path)
                .content(content)
                .originalValue(originalValue)
                .modifiedValue(modifiedValue)
                .build()
                .getLogDTO();
        operationLogService.add(logDTO);
    }
}
