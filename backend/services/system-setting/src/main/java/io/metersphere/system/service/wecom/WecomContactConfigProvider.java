package io.metersphere.system.service.wecom;

import io.metersphere.sdk.exception.MSException;
import io.metersphere.system.domain.OrgWecomSyncConfig;
import io.metersphere.system.domain.OrgWecomSyncConfigExample;
import io.metersphere.system.mapper.OrgWecomSyncConfigMapper;
import jakarta.annotation.Resource;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WecomContactConfigProvider {

    @Resource
    private OrgWecomSyncConfigMapper orgWecomSyncConfigMapper;

    public OrgWecomSyncConfig getByOrganizationId(String organizationId) {
        if (StringUtils.isBlank(organizationId)) {
            throw new MSException("organization_id is required");
        }
        OrgWecomSyncConfigExample example = new OrgWecomSyncConfigExample();
        example.createCriteria().andOrganizationIdEqualTo(organizationId);
        List<OrgWecomSyncConfig> configs = orgWecomSyncConfigMapper.selectByExample(example);
        if (CollectionUtils.isEmpty(configs)) {
            throw new MSException("org wecom sync config not found for organization: " + organizationId);
        }
        return configs.getFirst();
    }
}
