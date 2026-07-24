package io.metersphere.functional.hub.service;

import io.metersphere.sdk.constants.DefaultHubConstants;
import io.metersphere.system.domain.SystemParameter;
import io.metersphere.system.mapper.SystemParameterMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 枢纽灰度开关：系统参数 default.hub.sync.enabled，缺省 true
 */
@Service
public class DefaultHubConfigService {

    @Resource
    private SystemParameterMapper systemParameterMapper;

    public boolean isSyncEnabled() {
        SystemParameter param = systemParameterMapper.selectByPrimaryKey(DefaultHubConstants.SYNC_ENABLED_PARAM_KEY);
        if (param == null || StringUtils.isBlank(param.getParamValue())) {
            return true;
        }
        return !StringUtils.equalsAnyIgnoreCase(param.getParamValue(), "false", "0", "off", "disabled");
    }
}
