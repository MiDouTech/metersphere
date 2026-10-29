package io.metersphere.system.service;

import io.metersphere.sdk.constants.TemplateScene;
import io.metersphere.system.domain.OrganizationParameter;
import io.metersphere.system.mapper.OrganizationParameterMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static io.metersphere.sdk.constants.OrganizationParameterConstants.*;

/**
 * @author jianxing
 * @date : 2023-9-20
 */
@Service
@Transactional(rollbackFor = Exception.class)
public class BaseOrganizationParameterService {

    @Resource
    private OrganizationParameterMapper organizationParameterMapper;
    @Resource
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public String getValue(String orgId, String key) {
        OrganizationParameter organizationParameter = organizationParameterMapper.selectByPrimaryKey(orgId, key);
        return organizationParameter == null ? null : organizationParameter.getParamValue();
    }

    public String getOrgTemplateEnableKeyByScene(String scene) {
        Map<String, String> sceneMap = new HashMap<>();
        sceneMap.put(TemplateScene.FUNCTIONAL.name(), ORGANIZATION_FUNCTIONAL_TEMPLATE_ENABLE_KEY);
        sceneMap.put(TemplateScene.BUG.name(), ORGANIZATION_BUG_TEMPLATE_ENABLE_KEY);
        sceneMap.put(TemplateScene.API.name(), ORGANIZATION_API_TEMPLATE_ENABLE_KEY);
        sceneMap.put(TemplateScene.UI.name(), ORGANIZATION_UI_TEMPLATE_ENABLE_KEY);
        sceneMap.put(TemplateScene.TEST_PLAN.name(), ORGANIZATION_TEST_PLAN_TEMPLATE_ENABLE_KEY);
        return sceneMap.get(scene);
    }

    public void add(OrganizationParameter organizationParameter) {
        organizationParameterMapper.insert(organizationParameter);
    }

    /** Atomic assignment on the (organization_id, param_key) primary key, including replay. */
    public void setValue(String orgId, String key, String value) {
        if (org.apache.commons.lang3.StringUtils.isAnyBlank(orgId, key) || orgId.length() > 50 || key.length() > 50) {
            throw new io.metersphere.sdk.exception.MSException("组织参数不合法");
        }
        jdbcTemplate.update("INSERT INTO organization_parameter (organization_id, param_key, param_value) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE param_value=VALUES(param_value)", orgId, key, value);
    }
}
