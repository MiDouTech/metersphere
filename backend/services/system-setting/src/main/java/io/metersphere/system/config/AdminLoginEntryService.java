package io.metersphere.system.config;

import io.metersphere.system.domain.SystemParameter;
import io.metersphere.system.mapper.SystemParameterMapper;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 管理员账密登录入口（/#/login/admin）开关，读取 system_parameter。
 * 缺省 / false：入口关闭。
 */
@Service
public class AdminLoginEntryService {

    public static final String PARAM_KEY = "ui.login.admin.enabled";

    @Resource
    private SystemParameterMapper systemParameterMapper;

    public boolean isAdminLoginEntryEnabled() {
        SystemParameter param = systemParameterMapper.selectByPrimaryKey(PARAM_KEY);
        if (param == null || StringUtils.isBlank(param.getParamValue())) {
            return false;
        }
        return StringUtils.equalsAnyIgnoreCase(param.getParamValue().trim(), "true", "1", "on", "enabled");
    }
}
